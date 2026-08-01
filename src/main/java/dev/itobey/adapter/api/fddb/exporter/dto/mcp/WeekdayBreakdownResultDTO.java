package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.WeekdayStatsDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of an MCP weekday breakdown: the daily averages grouped by day of the week.
 * <p>
 * The bounds are echoed because both are optional - when neither is given the breakdown covers the
 * whole diary, and {@code null} bounds say so more clearly than an invented date pair would.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekdayBreakdownResultDTO {

    /**
     * The first day considered, or null when the breakdown starts at the first entry of the diary.
     */
    private LocalDate fromDate;

    /**
     * The last day considered, or null when the breakdown ends at the last entry of the diary.
     */
    private LocalDate toDate;

    /**
     * The number of logged days the breakdown rests on, summed over all days of the week.
     */
    private long loggedDays;

    /**
     * The averages per day of the week, Monday first. A day of the week without a single entry is
     * absent rather than reported as zero.
     */
    private List<WeekdayStatsDTO> weekdays;
}
