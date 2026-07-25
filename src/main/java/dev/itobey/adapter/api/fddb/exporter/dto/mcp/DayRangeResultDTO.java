package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of an MCP lookup for a date range.
 * <p>
 * The resolved bounds are echoed so the agent can see which dates a relative alias expanded to, and
 * {@code daysRequested} next to {@code entryCount} makes the gaps in the data visible without a
 * second call.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayRangeResultDTO {

    private LocalDate fromDate;

    private LocalDate toDate;

    /**
     * The number of days the range spans, both bounds inclusive.
     */
    private long daysRequested;

    /**
     * The number of days in the range that actually have an entry.
     */
    private int entryCount;

    /**
     * Whether the product lists are part of this response.
     */
    private boolean includeProducts;

    private List<FddbDataDTO> entries;
}
