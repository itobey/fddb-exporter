package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contains rolling averages for a given date range.
 * Used for statistics reporting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Rolling averages for a date range")
public class RollingAveragesDTO {
    @Schema(description = "Start date of the range", example = "2024-01-01")
    String fromDate;

    @Schema(description = "End date of the range", example = "2024-12-31")
    String toDate;

    @Schema(description = "Average nutritional values for the date range")
    StatsDTO.Averages averages;
}
