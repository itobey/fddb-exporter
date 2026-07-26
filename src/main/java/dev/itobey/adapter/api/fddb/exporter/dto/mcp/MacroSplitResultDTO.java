package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.MacroSplitDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * The result of an MCP macro split over a date range.
 * <p>
 * Same shape and same reasoning as {@link AveragesResultDTO}: the split is derived from the daily
 * averages, so a range with nothing logged has nothing to split, and how many days it rests on
 * decides how much the percentages are worth.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MacroSplitResultDTO {

    private LocalDate fromDate;

    private LocalDate toDate;

    /**
     * The length of the range in days, whether logged or not.
     */
    private long daysInRange;

    /**
     * How many of those days have an entry - the number the split rests on.
     */
    private long loggedDays;

    /**
     * Whether a single day in the range was logged at all.
     */
    private boolean found;

    /**
     * A plain-language explanation, only set when nothing was logged.
     */
    private String message;

    /**
     * The kcal-weighted split over the logged days. Null when nothing was logged.
     */
    private MacroSplitDTO split;
}
