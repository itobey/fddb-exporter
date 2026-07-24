package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * One bucket of a trend time series. For {@link TrendGranularity#DAY} a bucket is a single day,
 * so {@code average} and {@code total} are identical and {@code dayCount} is 1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One bucket of a nutritional trend time series")
public class TrendPointDTO {

    @Schema(description = "Label of the bucket: the date for DAY, ISO week for WEEK, year-month for MONTH", example = "2024-W03")
    private String bucket;

    @Schema(description = "First day of the bucket that lies within the queried range", example = "2024-01-15")
    private LocalDate fromDate;

    @Schema(description = "Last day of the bucket that lies within the queried range", example = "2024-01-21")
    private LocalDate toDate;

    @Schema(description = "Number of days with an entry inside this bucket", example = "7")
    private long dayCount;

    @Schema(description = "Average value of the metric across the days with an entry", example = "2143.7")
    private double average;

    @Schema(description = "Summed value of the metric across the days with an entry", example = "15005.9")
    private double total;
}
