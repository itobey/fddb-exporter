package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.mcp.DayResultDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;

/**
 * The MCP resources: the same data the tools return, addressable by URI.
 * <p>
 * A resource is pulled into the context by the client rather than called by the model, which makes
 * it the right shape for the things a conversation about this diary starts from - what the dataset
 * looks like, what one day held, what the fields mean. Nothing here is new capability; it is the
 * cheap entry point to what {@code get_stats}, {@code get_day} and {@code get_data_schema} already
 * do.
 * <p>
 * Only bounded payloads are exposed. A CSV export or an "everything" resource would be the same
 * context-window problem the tools spend their caps avoiding, without the caps.
 * <p>
 * Resource methods may only return a String (or SDK content types), so the JSON is written here
 * rather than by the framework. It goes through the MCP SDK's own mapper, which is what serializes
 * the tool results too - the app's {@code ObjectMapper} would render dates as numeric arrays and
 * make a day resource read differently from the identical tool response.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = {"fddb-exporter.mcp.enabled", "fddb-exporter.persistence.mongodb.enabled"},
        havingValue = "true")
public class FddbResources {

    private static final McpJsonMapper JSON_MAPPER = McpJsonDefaults.getMapper();

    private final FddbDataService fddbDataService;
    private final FddbSchemaTools fddbSchemaTools;

    @McpResource(
            uri = "fddb://stats",
            name = "diary_stats",
            title = "Diary statistics",
            description = "The global overview of the whole diary: entry count, first and last "
                    + "entry, coverage, unique and total products, all-time averages, the highest "
                    + "day per nutrient and the logging streaks. The same data as the get_stats tool.",
            mimeType = "application/json")
    public String stats() {
        log.debug("MCP: reading the stats resource");
        return toJson(fddbDataService.getStats());
    }

    /**
     * The {@code {date}} template variable carries no parameter annotation on purpose, unlike every
     * tool parameter here. {@code @McpArg} is only read for prompt arguments, and the MCP
     * {@code ResourceTemplate} has no per-variable description field for a client to render - the
     * only place the accepted forms can reach a client is the resource description above, which is
     * why it spells them out there.
     */
    @McpResource(
            uri = "fddb://day/{date}",
            name = "diary_day",
            title = "One day of the diary",
            description = "The daily totals and the full product list for a single day. The date is "
                    + McpDateParser.ACCEPTED_FORMATS + ". A day that was never logged comes back "
                    + "with found=false rather than as an error.",
            mimeType = "application/json")
    public String day(String date) {
        LocalDate resolvedDate = McpDateParser.parse(date);
        log.debug("MCP: reading the day resource for {}", resolvedDate);

        return toJson(fddbDataService.findByDate(resolvedDate.toString())
                .map(entry -> {
                    entry.setId(null);
                    return DayResultDTO.builder().date(resolvedDate).found(true).entry(entry).build();
                })
                .orElseGet(() -> DayResultDTO.builder()
                        .date(resolvedDate)
                        .found(false)
                        .message("No entry was logged for " + resolvedDate)
                        .build()));
    }

    @McpResource(
            uri = "fddb://schema",
            name = "data_schema",
            title = "Data dictionary",
            description = "Every field of a day entry and of a product, its unit, and the pitfalls "
                    + "of interpreting them. The same text as the get_data_schema tool.",
            mimeType = "text/markdown")
    public String schema() {
        log.debug("MCP: reading the schema resource");
        return fddbSchemaTools.getDataSchema();
    }

    private String toJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (IOException ioException) {
            throw new IllegalStateException("Could not serialize the resource: " + ioException.getMessage(),
                    ioException);
        }
    }
}
