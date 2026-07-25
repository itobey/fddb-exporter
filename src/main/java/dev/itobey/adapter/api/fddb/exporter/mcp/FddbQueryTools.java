package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.DayRangeResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.DayResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.ProductSearchResultDTO;
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
 * MCP tools for reading the exported diary: single days, date ranges and product occurrences.
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
            @McpToolParam(description = "The day to look up: an ISO date (YYYY-MM-DD), 'today', "
                    + "'yesterday' or 'N_days_ago'")
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
            @McpToolParam(description = "First day of the range (inclusive): an ISO date "
                    + "(YYYY-MM-DD), 'today', 'yesterday' or 'N_days_ago'")
            String fromDate,

            @McpToolParam(description = "Last day of the range (inclusive): an ISO date "
                    + "(YYYY-MM-DD), 'today', 'yesterday' or 'N_days_ago'")
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

            @McpToolParam(description = "Optional first day to include (inclusive): an ISO date "
                    + "(YYYY-MM-DD), 'today', 'yesterday' or 'N_days_ago'", required = false)
            String fromDate,

            @McpToolParam(description = "Optional last day to include (inclusive): an ISO date "
                    + "(YYYY-MM-DD), 'today', 'yesterday' or 'N_days_ago'", required = false)
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
