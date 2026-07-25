package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * MCP tools for the aggregated view of the diary.
 * <p>
 * These answer "how much on average" and "what does the whole dataset look like" without pulling
 * the underlying days into the client's context, which is what makes them worth having next to
 * {@link FddbQueryTools#getDays}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = {"fddb-exporter.mcp.enabled", "fddb-exporter.persistence.mongodb.enabled"},
        havingValue = "true")
public class FddbStatsTools {

    private final FddbDataService fddbDataService;

    @McpTool(
            name = "get_stats",
            description = """
                    Returns the global overview of the whole diary: how many entries exist, the date \
                    of the first and the last one, how much of that window is actually logged, the \
                    number of unique and total products, the all-time daily averages, the single \
                    highest day per nutrient and the current and longest logging streak. Call this \
                    first when a question needs to be anchored in time - it is the cheapest way to \
                    learn which period the data actually covers.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public StatsDTO getStats() {
        log.debug("MCP: retrieving global stats");
        return fddbDataService.getStats();
    }

    @McpTool(
            name = "get_averages",
            description = """
                    Returns the average daily calories, fat, carbs, sugar, protein and fibre over a \
                    date range, both bounds inclusive. Only days that have an entry are averaged, so \
                    days the user forgot to log do not drag the average down - call get_days for the \
                    same range if the number of logged days matters for the answer.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public RollingAveragesDTO getAverages(
            @McpToolParam(description = "First day of the range (inclusive): an ISO date "
                    + "(YYYY-MM-DD), 'today', 'yesterday' or 'N_days_ago'", required = true)
            String fromDate,

            @McpToolParam(description = "Last day of the range (inclusive): an ISO date "
                    + "(YYYY-MM-DD), 'today', 'yesterday' or 'N_days_ago'", required = true)
            String toDate) {
        LocalDate from = McpDateParser.parse(fromDate);
        LocalDate to = McpDateParser.parse(toDate);
        log.debug("MCP: retrieving averages for {} to {}", from, to);

        return fddbDataService.getRollingAverages(DateRangeDTO.builder()
                .fromDate(from.toString())
                .toDate(to.toString())
                .build());
    }
}
