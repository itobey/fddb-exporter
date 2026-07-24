package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;

/**
 * Average nutritional values for a single day of the week, aggregated over all matching entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Average nutritional values grouped by day of the week")
public class WeekdayStatsDTO {

    @Schema(description = "Day of the week", example = "SATURDAY")
    private DayOfWeek dayOfWeek;

    @Schema(description = "Number of entries that fell on this day of the week", example = "52")
    private long dayCount;

    @Schema(description = "Average nutritional values for this day of the week")
    private StatsDTO.Averages averages;
}
