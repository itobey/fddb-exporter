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
                    Returns the daily totals and every product logged for a single day. found=false \
                    means nothing was logged that day - not that the user ate nothing.""",
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
                    Returns the daily totals for a date range, oldest first, at most 366 days. \
                    Product lists are omitted unless includeProducts is set, because a long range \
                    with them is a very large response - ask for them only when the question is \
                    about what was eaten rather than about the totals.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public DayRangeResultDTO getDays(
            @McpToolParam(description = "First day: " + McpDateParser.ACCEPTED_FORMATS)
            String fromDate,

            @McpToolParam(description = "Last day: " + McpDateParser.ACCEPTED_FORMATS)
            String toDate,

            @McpToolParam(description = "Include each day's products. Defaults to false",
                    required = false)
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
                    was logged, the amount and its macros. Returns the occurrences themselves, for \
                    "when exactly" questions - if the question is only what a product is called, \
                    call list_distinct_products instead, which returns the names alone. The name is \
                    matched as a case-insensitive substring; FDDB names are usually German and \
                    brand-prefixed, so if a search comes back empty, try a shorter fragment. It is \
                    capped, so when truncated is true the list is only the newest slice of the \
                    matches: do not add up or count what came back to answer "how much" or "how \
                    often" - call get_product_summary, which is uncapped and counts them all.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ProductSearchResultDTO searchProducts(
            @McpToolParam(description = "Case-insensitive substring of the product name")
            String name,

            @McpToolParam(description = "Optional days of the week to restrict to, as English "
                    + "upper-case names, e.g. ['MONDAY', 'FRIDAY']", required = false)
            List<String> daysOfWeek,

            @McpToolParam(description = "Optional first day: " + McpDateParser.ACCEPTED_FORMATS,
                    required = false)
            String fromDate,

            @McpToolParam(description = "Optional last day: " + McpDateParser.ACCEPTED_FORMATS,
                    required = false)
            String toDate,

            @McpToolParam(description = "How many occurrences to return, at most 500. Defaults to "
                    + "100", required = false)
            Integer limit) {
        LocalDate from = McpDateParser.parseOptional(fromDate);
        LocalDate to = McpDateParser.parseOptional(toDate);
        List<DayOfWeek> days = parseDaysOfWeek(daysOfWeek);
        int effectiveLimit = McpPage.boundedLimit(limit, DEFAULT_PRODUCT_SEARCH_LIMIT, MAX_PRODUCT_SEARCH_LIMIT);
        log.debug("MCP: searching products matching '{}' in {} to {} (limit={})", name, from, to, effectiveLimit);

        McpPage<ProductWithDateDTO> page = McpPage.fetch(effectiveLimit,
                max -> fddbDataService.findByProduct(name, days, from, to, max));

        return ProductSearchResultDTO.builder()
                .searchTerm(name)
                .fromDate(from)
                .toDate(to)
                .resultCount(page.size())
                .limit(effectiveLimit)
                .truncated(page.truncated())
                .results(page.items())
                .build();
    }

    @McpTool(
            name = "list_top_products",
            description = """
                    Ranks the products in the diary either by how often they were logged (FREQUENCY) \
                    or by the total amount of a nutrient they contributed, optionally within a date \
                    range - "what do I actually eat the most", "where do my calories come from". The \
                    totals are sums across every logged occurrence, so a product eaten daily in \
                    small portions can outrank a rare large one.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public TopProductsResultDTO listTopProducts(
            @McpToolParam(description = "What to rank by: FREQUENCY, CALORIES, FAT, CARBS or PROTEIN. "
                    + "Defaults to FREQUENCY", required = false)
            ProductRanking by,

            @McpToolParam(description = "Optional first day: " + McpDateParser.ACCEPTED_FORMATS,
                    required = false)
            String fromDate,

            @McpToolParam(description = "Optional last day: " + McpDateParser.ACCEPTED_FORMATS,
                    required = false)
            String toDate,

            @McpToolParam(description = "How many products to return, at most 100. Defaults to 20",
                    required = false)
            Integer limit) {
        LocalDate from = McpDateParser.parseOptional(fromDate);
        LocalDate to = McpDateParser.parseOptional(toDate);
        ProductRanking ranking = by == null ? ProductRanking.FREQUENCY : by;
        int effectiveLimit = McpPage.boundedLimit(limit, DEFAULT_TOP_PRODUCTS_LIMIT, MAX_TOP_PRODUCTS_LIMIT);
        log.debug("MCP: ranking products by {} in {} to {} (limit={})", ranking, from, to, effectiveLimit);

        McpPage<TopProductDTO> page = McpPage.fetch(effectiveLimit,
                max -> fddbDataService.getTopProducts(ranking, from, to, max));

        return TopProductsResultDTO.builder()
                .rankedBy(ranking)
                .fromDate(from)
                .toDate(to)
                .resultCount(page.size())
                .limit(effectiveLimit)
                .truncated(page.truncated())
                .results(page.items())
                .build();
    }

    @McpTool(
            name = "get_product_summary",
            description = """
                    Aggregates every product matching a search term into one figure set: times \
                    logged, first and last eaten, the totals, the averages and weekdayDistribution, \
                    the count of occurrences per day of the week. The only product tool without a \
                    cap: it folds in every match however many there are, so timesEaten and the \
                    totals are exact where the same numbers counted off a search_products page are \
                    only the newest slice. Use it for every "how much", "how often" and "which \
                    weekday" question, and over a range of months prefer it even when the capped \
                    tools look sufficient. Read matchedProductNames before treating the result as \
                    one food - a short term folds several brands into one number.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ProductSummaryResultDTO getProductSummary(
            @McpToolParam(description = "Case-insensitive substring of the product name")
            String name,

            @McpToolParam(description = "Optional first day: " + McpDateParser.ACCEPTED_FORMATS,
                    required = false)
            String fromDate,

            @McpToolParam(description = "Optional last day: " + McpDateParser.ACCEPTED_FORMATS,
                    required = false)
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
                    The vocabulary lookup: the deduplicated product names alone - no dates, no \
                    amounts, no macros - optionally filtered by a case-insensitive substring. Use \
                    it when the question is what something is called in the diary rather than when \
                    or how much of it was eaten, and to resolve the user's wording to a real name \
                    before searching: FDDB names are long, usually German and brand-prefixed, and \
                    'flocken' finds 'Haferflocken kernig' because the filter matches anywhere in \
                    the name, not just at the start. For the occurrences behind a name, call \
                    search_products.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public DistinctProductsResultDTO listDistinctProducts(
            @McpToolParam(description = "Optional case-insensitive substring the names must contain. "
                    + "Omit to list from the whole diary", required = false)
            String search,

            @McpToolParam(description = "How many names to return, at most 500. Defaults to 50",
                    required = false)
            Integer limit) {
        int effectiveLimit = McpPage.boundedLimit(limit, DEFAULT_DISTINCT_PRODUCTS_LIMIT, MAX_DISTINCT_PRODUCTS_LIMIT);
        log.debug("MCP: listing distinct product names matching '{}' (limit={})", search, effectiveLimit);

        McpPage<String> page = McpPage.fetch(effectiveLimit,
                max -> fddbDataService.findDistinctProductNames(search, max));

        return DistinctProductsResultDTO.builder()
                .searchTerm(search)
                .resultCount(page.size())
                .limit(effectiveLimit)
                .truncated(page.truncated())
                .names(page.items())
                .build();
    }

    @McpTool(
            name = "find_days_with_products",
            description = """
                    Finds the days on which at least one product matching any of the include \
                    keywords was logged, skipping days where a matching product also matches an \
                    exclude keyword. Keywords are case-insensitive substrings of the product name. \
                    The building block for elimination-diet questions - get the days here, then line \
                    them up with correlate_products_with_dates or pull individual days with get_day. \
                    Grouped by day, newest first. Answer "on how many days did I eat X?" with \
                    matchedDayCount, which is how many exist, not with dayCount, which is how many \
                    came back.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public DaysWithProductsResultDTO findDaysWithProducts(
            @McpToolParam(description = "One or more keywords, each a case-insensitive substring of "
                    + "a product name. A day matches when any of them does")
            List<String> includeKeywords,

            @McpToolParam(description = "Optional keywords that disqualify a product: an occurrence "
                    + "whose name contains one of these is not counted", required = false)
            List<String> excludeKeywords,

            @McpToolParam(description = "Optional earliest day: " + McpDateParser.ACCEPTED_FORMATS
                    + ". Omit for the whole diary", required = false)
            String startDate,

            @McpToolParam(description = "How many days to return, at most 366. Defaults to 100",
                    required = false)
            Integer limit) {
        if (includeKeywords == null || includeKeywords.isEmpty()) {
            throw new IllegalArgumentException("At least one include keyword is required - without one "
                    + "this would return every day in the diary");
        }
        LocalDate start = McpDateParser.parseOptional(startDate);
        int effectiveLimit = McpPage.boundedLimit(limit, DEFAULT_MATCHED_DAYS_LIMIT, MAX_MATCHED_DAYS_LIMIT);
        log.debug("MCP: finding days with {} (excluding {}) from {}", includeKeywords, excludeKeywords, start);

        // grouped and capped in the database already
        McpPage<DayWithProductsDTO> page = McpPage.fetch(effectiveLimit,
                max -> fddbDataService.findDaysWithProducts(includeKeywords, excludeKeywords, start, max));

        // an untruncated result already holds every match, so the totals cost a second query only
        // when the answer would otherwise be a guess
        ProductDayTotalsDTO totals = page.truncated()
                ? fddbDataService.countDaysWithProducts(includeKeywords, excludeKeywords, start)
                : ProductDayTotalsDTO.builder()
                .dayCount(page.size())
                .occurrenceCount(page.items().stream().mapToLong(DayWithProductsDTO::getOccurrences).sum())
                .build();

        return DaysWithProductsResultDTO.builder()
                .includeKeywords(includeKeywords)
                .excludeKeywords(excludeKeywords == null || excludeKeywords.isEmpty() ? null : excludeKeywords)
                .startDate(start)
                .dayCount(page.size())
                .matchedDayCount(totals.getDayCount())
                .occurrenceCount(totals.getOccurrenceCount())
                .truncated(page.truncated())
                .days(page.items().stream()
                        .map(day -> DaysWithProductsResultDTO.MatchedDay.builder()
                                .date(day.getDate())
                                .products(day.getProducts())
                                .build())
                        .toList())
                .build();
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
