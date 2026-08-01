package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of an MCP trend query: one metric over time, bucketed by day, ISO week or month.
 * <p>
 * {@code loggedDays} next to the span of the range makes the coverage of the trend visible at a
 * glance, which matters because empty buckets are omitted rather than reported as zero: a week with
 * two logged days and a week with seven look identical otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendResultDTO {

    private NutrientMetric metric;

    private TrendGranularity granularity;

    /**
     * The unit of {@code average} and {@code total} of each bucket: kcal for calories, grams for
     * everything else.
     */
    private String unit;

    private LocalDate fromDate;

    private LocalDate toDate;

    /**
     * The number of buckets in this response. Buckets without a single entry are absent.
     */
    private int bucketCount;

    /**
     * The total number of logged days across all buckets.
     */
    private long loggedDays;

    private List<TrendPointDTO> buckets;
}
