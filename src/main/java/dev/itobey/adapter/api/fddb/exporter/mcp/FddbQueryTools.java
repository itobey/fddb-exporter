package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.*;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * MCP tools for reading the exported diary: single days, date ranges, product occurrences, the
 * ranking and the aggregate of the products across them, and the vocabulary of names to search with.
 * <p>
 * Unlike the Vaadin UI, which goes through the REST API over HTTP, the tools call
 * {@link FddbDataService} directly - there is no benefit to an in-process HTTP hop, and it keeps
 * MCP availability independent of the server's own host and port configuration.
 * <p>
 * The tools are only registered when both the MCP server and MongoDB persistence are enabled: every
 * query below is product- or document-level and cannot be served by InfluxDB, and a tool that
 * always fails is worse than an absent tool.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = {"fddb-exporter.mcp.enabled", "fddb-exporter.persistence.mongodb.enabled"},
        havingValue = "true")
public class FddbQueryTools {

    /**
     * Default cap for product searches. High enough for "how often do I eat oats" over a year,
     * low enough not to flood the client's context window.
     */
    private static final int DEFAULT_PRODUCT_SEARCH_LIMIT = 100;

    private static final int MAX_PRODUCT_SEARCH_LIMIT = 500;

    /**
     * Default cap for the product ranking. A ranking is read from the top down, so a long tail adds
     * payload without adding insight.
     */
    private static final int DEFAULT_TOP_PRODUCTS_LIMIT = 20;

    private static final int MAX_TOP_PRODUCTS_LIMIT = 100;

    /**
     * Default cap for the vocabulary lookup. Enough to see the variants of a search term, and a
     * plain name is cheap enough that a caller wanting the whole list can ask for it.
     */
    private static final int DEFAULT_DISTINCT_PRODUCTS_LIMIT = 50;

    private static final int MAX_DISTINCT_PRODUCTS_LIMIT = 500;

    /**
     * Default cap for the keyword day search. A year of days is already a lot to reason about, and
     * the count of matches is reported regardless of how many days come back.
     */
    private static final int DEFAULT_MATCHED_DAYS_LIMIT = 100;

    private static final int MAX_MATCHED_DAYS_LIMIT = 366;

    private final FddbDataService fddbDataService;

    @McpTool(
            name = "get_day",
            description = """
                    Returns the logged nutrition data for a single day: the daily totals and every \
                    product logged that day. Calories are kcal, all other nutrients are grams. \
                    A day with no entry is reported as found=false, which means nothing was logged \
                    that day - it does not mean the user ate nothing.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public DayResultDTO getDay(
            @McpToolParam(description = "The day to look up: " + McpDateParser.ACCEPTED_FORMATS)
            String date) {
        LocalDate resolvedDate = McpDateParser.parse(date);
        log.debug("MCP: retrieving day {}", resolvedDate);

        Optional<FddbDataDTO> entry = fddbDataService.findByDate(resolvedDate.toString());
        return entry
                .map(data -> DayResultDTO.builder()
                        .date(resolvedDate)
                        .found(true)
                        .entry(withoutId(data))
                        .build())
                .orElseGet(() -> DayResultDTO.builder()
                        .date(resolvedDate)
                        .found(false)
                        .message("No entry was logged for " + resolvedDate)
                        .build());
    }

    @McpTool(
            name = "get_days",
            description = """
                    Returns the daily nutrition totals for a date range, both bounds inclusive and \
                    oldest first. Days without an entry are simply absent from the result. Product \
                    lists are omitted unless includeProducts is set, because a long range with \
                    products is a very large response - ask for them only when the question is about \
                    what was eaten rather than about the totals. The range is limited to 366 days.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public DayRangeResultDTO getDays(
            @McpToolParam(description = "First day of the range (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS)
            String fromDate,

            @McpToolParam(description = "Last day of the range (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS)
            String toDate,

            @McpToolParam(description = "Whether to include the products logged on each day. "
                    + "Defaults to false", required = false)
            Boolean includeProducts) {
        LocalDate from = McpDateParser.parse(fromDate);
        LocalDate to = McpDateParser.parse(toDate);
        boolean withProducts = Boolean.TRUE.equals(includeProducts);
        log.debug("MCP: retrieving days {} to {} (includeProducts={})", from, to, withProducts);

        List<FddbDataDTO> entries = fddbDataService.findByDateRange(from, to, withProducts);
        entries.forEach(this::withoutId);

        return DayRangeResultDTO.builder()
                .fromDate(from)
                .toDate(to)
                .daysRequested(DAYS.between(from, to) + 1)
                .entryCount(entries.size())
                .includeProducts(withProducts)
                .entries(entries)
                .build();
    }

    @McpTool(
            name = "search_products",
            description = """
                    Finds every occurrence of a product in the diary, newest first, with the date it \
                    was logged, the amount and its macros. The name is matched as a case-insensitive \
                    substring, so 'hafer' also finds 'Haferflocken kernig'. Product names come from \
                    FDDB and are usually German and brand-prefixed - if a search comes back empty, \
                    try a shorter fragment. Check the 'truncated' flag before deriving any count \
                    from the result: when it is true, more occurrences exist than were returned.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ProductSearchResultDTO searchProducts(
            @McpToolParam(description = "Case-insensitive substring of the product name")
            String name,

            @McpToolParam(description = "Optional days of the week to restrict the search to, as "
                    + "English upper-case names, e.g. ['MONDAY', 'FRIDAY']", required = false)
            List<String> daysOfWeek,

            @McpToolParam(description = "Optional first day to include (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = false)
            String fromDate,

            @McpToolParam(description = "Optional last day to include (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = false)
            String toDate,

            @McpToolParam(description = "Maximum number of occurrences to return, at most 500. "
                    + "Defaults to 100", required = false)
            Integer limit) {
        LocalDate from = McpDateParser.parseOptional(fromDate);
        LocalDate to = McpDateParser.parseOptional(toDate);
        List<DayOfWeek> days = parseDaysOfWeek(daysOfWeek);
        int effectiveLimit = effectiveLimit(limit);
        log.debug("MCP: searching products matching '{}' in {} to {} (limit={})", name, from, to, effectiveLimit);

        // one more than asked for, so an overflow can be reported instead of silently truncating
        List<ProductWithDateDTO> matches =
                fddbDataService.findByProduct(name, days, from, to, effectiveLimit + 1);
        boolean truncated = matches.size() > effectiveLimit;
        List<ProductWithDateDTO> results = truncated ? matches.subList(0, effectiveLimit) : matches;

        return ProductSearchResultDTO.builder()
                .searchTerm(name)
                .fromDate(from)
                .toDate(to)
                .resultCount(results.size())
                .limit(effectiveLimit)
                .truncated(truncated)
                .results(results)
                .build();
    }

    @McpTool(
            name = "list_top_products",
            description = """
                    Ranks the products in the diary either by how often they were logged (FREQUENCY) \
                    or by the total amount of a nutrient they contributed, optionally restricted to a \
                    date range. This is the tool for "what do I actually eat the most" and "where do \
                    my calories come from". The totals are sums across every logged occurrence, so a \
                    product eaten daily in small portions can outrank a rare large one. Check the \
                    'truncated' flag before calling anything "the top" of the list.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public TopProductsResultDTO listTopProducts(
            @McpToolParam(description = "What to rank by: FREQUENCY, CALORIES, FAT, CARBS or PROTEIN. "
                    + "Defaults to FREQUENCY", required = false)
            ProductRanking by,

            @McpToolParam(description = "Optional first day to include (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = false)
            String fromDate,

            @McpToolParam(description = "Optional last day to include (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = false)
            String toDate,

            @McpToolParam(description = "How many products to return, at most 100. Defaults to 20",
                    required = false)
            Integer limit) {
        LocalDate from = McpDateParser.parseOptional(fromDate);
        LocalDate to = McpDateParser.parseOptional(toDate);
        ProductRanking ranking = by == null ? ProductRanking.FREQUENCY : by;
        int effectiveLimit = limit == null || limit <= 0
                ? DEFAULT_TOP_PRODUCTS_LIMIT
                : Math.min(limit, MAX_TOP_PRODUCTS_LIMIT);
        log.debug("MCP: ranking products by {} in {} to {} (limit={})", ranking, from, to, effectiveLimit);

        // one more than asked for, so an overflow can be reported instead of silently truncating
        List<TopProductDTO> ranked = fddbDataService.getTopProducts(ranking, from, to, effectiveLimit + 1);
        boolean truncated = ranked.size() > effectiveLimit;
        List<TopProductDTO> results = truncated ? ranked.subList(0, effectiveLimit) : ranked;

        return TopProductsResultDTO.builder()
                .rankedBy(ranking)
                .fromDate(from)
                .toDate(to)
                .resultCount(results.size())
                .limit(effectiveLimit)
                .truncated(truncated)
                .results(results)
                .build();
    }

    @McpTool(
            name = "get_product_summary",
            description = """
                    Aggregates every product matching a search term into one figure set: how often \
                    it was logged, when it was first and last eaten, what it contributed in total \
                    and on average, and how the occurrences spread over the days of the week. Use \
                    this instead of search_products when the question is "how much" or "how often" \
                    rather than "when exactly". Read matchedProductNames before treating the result \
                    as one food - a short search term folds several brands into one number.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ProductSummaryResultDTO getProductSummary(
            @McpToolParam(description = "Case-insensitive substring of the product name")
            String name,

            @McpToolParam(description = "Optional first day to include (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = false)
            String fromDate,

            @McpToolParam(description = "Optional last day to include (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = false)
            String toDate) {
        LocalDate from = McpDateParser.parseOptional(fromDate);
        LocalDate to = McpDateParser.parseOptional(toDate);
        log.debug("MCP: summarizing products matching '{}' in {} to {}", name, from, to);

        ProductSummaryDTO summary = fddbDataService.getProductSummary(name, from, to);
        boolean found = summary.getTimesEaten() > 0;

        return ProductSummaryResultDTO.builder()
                .searchTerm(name)
                .fromDate(from)
                .toDate(to)
                .found(found)
                .message(found ? null : "No product matching '" + name + "' was logged in this range - "
                        + "try a shorter fragment, or call list_distinct_products to see the names "
                        + "the diary actually contains.")
                .summary(found ? summary : null)
                .build();
    }

    @McpTool(
            name = "list_distinct_products",
            description = """
                    Lists the distinct product names in the diary, optionally filtered by a \
                    case-insensitive substring. This is the vocabulary lookup: FDDB names are long, \
                    usually German and brand-prefixed, so resolving the user's wording to a real \
                    name here first saves an empty search later. 'flocken' finds 'Haferflocken \
                    kernig' - the filter matches anywhere in the name, not just at the start.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public DistinctProductsResultDTO listDistinctProducts(
            @McpToolParam(description = "Optional case-insensitive substring the names must contain. "
                    + "Omit to list from the whole diary", required = false)
            String search,

            @McpToolParam(description = "How many names to return, at most 500. Defaults to 50",
                    required = false)
            Integer limit) {
        int effectiveLimit = limit == null || limit <= 0
                ? DEFAULT_DISTINCT_PRODUCTS_LIMIT
                : Math.min(limit, MAX_DISTINCT_PRODUCTS_LIMIT);
        log.debug("MCP: listing distinct product names matching '{}' (limit={})", search, effectiveLimit);

        // one more than asked for, so an overflow can be reported instead of silently truncating
        List<String> names = fddbDataService.findDistinctProductNames(search, effectiveLimit + 1);
        boolean truncated = names.size() > effectiveLimit;
        List<String> results = truncated ? names.subList(0, effectiveLimit) : names;

        return DistinctProductsResultDTO.builder()
                .searchTerm(search)
                .resultCount(results.size())
                .limit(effectiveLimit)
                .truncated(truncated)
                .names(results)
                .build();
    }

    @McpTool(
            name = "find_days_with_products",
            description = """
                    Finds the days on which at least one product matching any of the include \
                    keywords was logged, skipping days where a matching product also matches an \
                    exclude keyword. Keywords are case-insensitive substrings of the product name. \
                    This is the building block for elimination-diet questions - get the days here, \
                    then line them up with correlate_products_with_dates or pull individual days \
                    with get_day. Results are grouped by day, newest first. dayCount is how many \
                    days came back and matchedDayCount how many exist - answer "on how many days \
                    did I eat X?" with matchedDayCount, which stays right even when truncated is \
                    set.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public DaysWithProductsResultDTO findDaysWithProducts(
            @McpToolParam(description = "One or more keywords, each a case-insensitive substring of "
                    + "a product name. A day matches when any of them does")
            List<String> includeKeywords,

            @McpToolParam(description = "Optional keywords that disqualify a product: an occurrence "
                    + "whose name contains one of these is not counted", required = false)
            List<String> excludeKeywords,

            @McpToolParam(description = "Optional earliest day to consider: "
                    + McpDateParser.ACCEPTED_FORMATS + ". Omit to search the whole diary",
                    required = false)
            String startDate,

            @McpToolParam(description = "How many days to return, at most 366. Defaults to 100",
                    required = false)
            Integer limit) {
        if (includeKeywords == null || includeKeywords.isEmpty()) {
            throw new IllegalArgumentException("At least one include keyword is required - without one "
                    + "this would return every day in the diary");
        }
        LocalDate start = McpDateParser.parseOptional(startDate);
        int effectiveLimit = limit == null || limit <= 0
                ? DEFAULT_MATCHED_DAYS_LIMIT
                : Math.min(limit, MAX_MATCHED_DAYS_LIMIT);
        log.debug("MCP: finding days with {} (excluding {}) from {}", includeKeywords, excludeKeywords, start);

        // grouped and capped in the database; one more than asked for, so an overflow is visible
        List<DayWithProductsDTO> matched =
                fddbDataService.findDaysWithProducts(includeKeywords, excludeKeywords, start, effectiveLimit + 1);
        boolean truncated = matched.size() > effectiveLimit;
        List<DayWithProductsDTO> returned = truncated ? matched.subList(0, effectiveLimit) : matched;

        // an untruncated result already holds every match, so the totals cost a second query only
        // when the answer would otherwise be a guess
        ProductDayTotalsDTO totals = truncated
                ? fddbDataService.countDaysWithProducts(includeKeywords, excludeKeywords, start)
                : ProductDayTotalsDTO.builder()
                .dayCount(matched.size())
                .occurrenceCount(matched.stream().mapToLong(DayWithProductsDTO::getOccurrences).sum())
                .build();

        return DaysWithProductsResultDTO.builder()
                .includeKeywords(includeKeywords)
                .excludeKeywords(excludeKeywords == null || excludeKeywords.isEmpty() ? null : excludeKeywords)
                .startDate(start)
                .dayCount(returned.size())
                .matchedDayCount(totals.getDayCount())
                .occurrenceCount(totals.getOccurrenceCount())
                .truncated(truncated)
                .days(returned.stream()
                        .map(day -> DaysWithProductsResultDTO.MatchedDay.builder()
                                .date(day.getDate())
                                .products(day.getProducts())
                                .build())
                        .toList())
                .build();
    }

    private int effectiveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PRODUCT_SEARCH_LIMIT;
        }
        return Math.min(limit, MAX_PRODUCT_SEARCH_LIMIT);
    }

    private List<DayOfWeek> parseDaysOfWeek(List<String> daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return List.of();
        }

        List<DayOfWeek> parsed = new ArrayList<>();
        for (String day : daysOfWeek) {
            try {
                parsed.add(DayOfWeek.valueOf(day.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException illegalArgumentException) {
                throw new DateTimeException("'" + day + "' is not a day of the week - use the English "
                        + "upper-case names, e.g. MONDAY");
            }
        }
        return parsed;
    }

    /**
     * Strips the database id, which is noise in an MCP response - nothing an agent can do with it.
     */
    private FddbDataDTO withoutId(FddbDataDTO entry) {
        entry.setId(null);
        return entry;
    }
}
