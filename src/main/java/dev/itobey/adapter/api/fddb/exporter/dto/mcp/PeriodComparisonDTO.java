package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * A side-by-side comparison of the average daily macros of two date ranges.
 * <p>
 * The deltas are computed server-side on purpose. An agent asked to compare two months can do the
 * subtraction itself, but it then has to keep twelve numbers straight while doing it - and a
 * misplaced percentage in an answer about someone's diet is worse than an extra field in a payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodComparisonDTO {

    /**
     * The period the deltas are measured for, usually the more recent one.
     */
    private Period periodA;

    /**
     * The period the deltas are measured against, usually the baseline.
     */
    private Period periodB;

    /**
     * One entry per macro, always in the same order. Empty when either period has no logged day at
     * all, since there is nothing to compare then.
     */
    private List<MetricDelta> deltas;

    /**
     * Set when the comparison could not be made, naming the period that has no data.
     */
    private String message;

    /**
     * One of the two compared ranges. {@code loggedDays} is the number of days the averages rest
     * on - it is not the length of the range, since unlogged days are left out of the averaging.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Period {

        private LocalDate fromDate;

        private LocalDate toDate;

        /**
         * The number of days the range spans, both bounds inclusive.
         */
        private long daysInRange;

        /**
         * The number of days in the range that have an entry, i.e. the days actually averaged.
         */
        private int loggedDays;

        /**
         * The average daily macros over the logged days, or null when there are none.
         */
        private StatsDTO.Averages averages;
    }

    /**
     * The change in one macro from {@code periodB} to {@code periodA}. A positive value means
     * period A is higher.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricDelta {

        private NutrientMetric metric;

        /**
         * The unit of every value below: kcal for calories, grams for everything else.
         */
        private String unit;

        private double periodA;

        private double periodB;

        /**
         * {@code periodA - periodB}.
         */
        private double absoluteChange;

        /**
         * The change relative to period B, in percent. Null when period B averages zero, because
         * the change from nothing is not a percentage.
         */
        private Double percentageChange;
    }
}
