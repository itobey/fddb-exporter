package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of checking every logged day of a range against a set of nutritional targets.
 * <p>
 * A day counts as met only when it passes <em>all</em> targets; the per-target breakdown exists so a
 * user who missed a combined goal can still see which half of it worked.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalCheckResultDTO {

    private LocalDate fromDate;

    private LocalDate toDate;

    /**
     * The number of days the range spans, both bounds inclusive.
     */
    private long daysInRange;

    /**
     * The number of days in the range that have an entry. Only these are evaluated - a day that was
     * never logged is neither a pass nor a fail.
     */
    private int daysEvaluated;

    /**
     * The number of evaluated days that passed every target.
     */
    private int daysMet;

    /**
     * The share of evaluated days that passed every target, in percent.
     */
    private double hitRate;

    /**
     * The longest run of consecutive calendar days that passed every target. An unlogged day breaks
     * the run: a goal cannot be claimed for a day that has no data.
     */
    private int longestStreak;

    /**
     * The run of consecutive days that passed every target, counted back from the end of the range.
     * <p>
     * Reads 0 whenever {@code toDate} itself has no entry, since an unlogged day breaks the run -
     * and the diary is normally scraped for yesterday, so a range ending today reports 0 almost
     * every time however well the days before it went. The tool description says so; a caller that
     * wants a meaningful current streak ends the range on the last day that can be logged.
     */
    private int currentStreak;

    /**
     * One entry per target, in the order they were given.
     */
    private List<TargetResult> targets;

    /**
     * The individual days, oldest first. Only present when the caller asked for them.
     */
    private List<DayResult> days;

    /**
     * Set when nothing could be evaluated, e.g. because the range has no logged day.
     */
    private String message;

    /**
     * How one target fared on its own, ignoring the other targets.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetResult {

        private NutrientMetric metric;

        private GoalComparator comparator;

        private double target;

        /**
         * The unit of {@code target} and {@code average}: kcal for calories, grams for everything else.
         */
        private String unit;

        /**
         * The number of evaluated days that passed this target.
         */
        private int daysMet;

        /**
         * The number of evaluated days that failed this target.
         */
        private int daysMissed;

        /**
         * The share of evaluated days that passed this target, in percent.
         */
        private double hitRate;

        /**
         * The average value of this metric across the evaluated days, for context on how far off a
         * missed target was.
         */
        private double average;
    }

    /**
     * The verdict for a single day.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayResult {

        private LocalDate date;

        /**
         * Whether the day passed every target.
         */
        private boolean met;

        /**
         * The targets this day failed, with the value it actually reached. Absent on a day that met
         * everything.
         */
        private List<MissedTarget> missed;
    }

    /**
     * A single failed target on a single day.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissedTarget {

        private NutrientMetric metric;

        private GoalComparator comparator;

        private double target;

        /**
         * The value the day actually reached for this metric.
         */
        private double actual;
    }
}
