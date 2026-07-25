package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.GoalCheckResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.GoalTargetDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.PeriodComparisonDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * The MCP tools that are not a wrapper around a stored query.
 * <p>
 * Both tools here work purely on top of what {@link FddbDataService} already returns - comparing two
 * periods is two averages and a subtraction, checking goals is a comparator per metric and a streak
 * count. That is tool-shaped rather than store-shaped logic, which is why it lives in the MCP layer
 * instead of being pushed into the service or into MongoDB.
 * <p>
 * Doing the arithmetic here rather than leaving it to the agent is the point: an LLM asked to compare
 * twelve numbers across two months will usually get it right, and the times it does not it still
 * sounds certain.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = {"fddb-exporter.mcp.enabled", "fddb-exporter.persistence.mongodb.enabled"},
        havingValue = "true")
public class FddbAnalysisTools {

    /**
     * The order the macros are reported in, kept identical across both tools so an agent sees the
     * same shape every time.
     */
    private static final List<NutrientMetric> REPORTED_METRICS = List.of(
            NutrientMetric.CALORIES, NutrientMetric.FAT, NutrientMetric.CARBS,
            NutrientMetric.SUGAR, NutrientMetric.PROTEIN, NutrientMetric.FIBRE);

    private final FddbDataService fddbDataService;

    @McpTool(
            name = "compare_periods",
            description = """
                    Compares the average daily nutrition of two date ranges and returns both sets of \
                    averages plus the absolute and percentage change per nutrient. Period A is the one \
                    being judged and period B is what it is judged against, so for "this month vs. \
                    last month" period A is this month: a positive change means period A is higher. \
                    Only logged days are averaged, and loggedDays is reported per period - a period \
                    with 5 logged days out of 30 does not support a conclusion. Each range is limited \
                    to 366 days.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public PeriodComparisonDTO comparePeriods(
            @McpToolParam(description = "First day of period A (inclusive): an ISO date (YYYY-MM-DD), "
                    + "'today', 'yesterday' or 'N_days_ago'")
            String periodAFrom,

            @McpToolParam(description = "Last day of period A (inclusive): an ISO date (YYYY-MM-DD), "
                    + "'today', 'yesterday' or 'N_days_ago'")
            String periodATo,

            @McpToolParam(description = "First day of period B, the baseline (inclusive): an ISO date "
                    + "(YYYY-MM-DD), 'today', 'yesterday' or 'N_days_ago'")
            String periodBFrom,

            @McpToolParam(description = "Last day of period B, the baseline (inclusive): an ISO date "
                    + "(YYYY-MM-DD), 'today', 'yesterday' or 'N_days_ago'")
            String periodBTo) {
        LocalDate aFrom = McpDateParser.parse(periodAFrom);
        LocalDate aTo = McpDateParser.parse(periodATo);
        LocalDate bFrom = McpDateParser.parse(periodBFrom);
        LocalDate bTo = McpDateParser.parse(periodBTo);
        log.debug("MCP: comparing {} to {} against {} to {}", aFrom, aTo, bFrom, bTo);

        PeriodComparisonDTO.Period periodA = summarize(aFrom, aTo);
        PeriodComparisonDTO.Period periodB = summarize(bFrom, bTo);

        if (periodA.getAverages() == null || periodB.getAverages() == null) {
            String empty = periodA.getAverages() == null ? "A" : "B";
            return PeriodComparisonDTO.builder()
                    .periodA(periodA)
                    .periodB(periodB)
                    .message("Period " + empty + " has no logged day, so there is nothing to compare")
                    .build();
        }

        return PeriodComparisonDTO.builder()
                .periodA(periodA)
                .periodB(periodB)
                .deltas(deltasBetween(periodA.getAverages(), periodB.getAverages()))
                .build();
    }

    @McpTool(
            name = "check_goals",
            description = """
                    Checks every logged day of a range against one or more nutritional targets and \
                    returns the hit rate, the longest and current streak, a breakdown per target and, \
                    on request, the individual days. A target is a nutrient, a direction and a value, \
                    e.g. {"metric":"PROTEIN","comparator":"AT_LEAST","value":120} or \
                    {"metric":"CALORIES","comparator":"AT_MOST","value":2200}; several targets can be \
                    combined and a day only counts as met when it passes all of them. Values are kcal \
                    for CALORIES and grams for every other nutrient. The app stores no goals of its \
                    own - pass whatever the user states. Days without an entry are not evaluated, but \
                    they do break a streak, since a goal cannot be claimed for a day with no data.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public GoalCheckResultDTO checkGoals(
            @McpToolParam(description = "First day to check (inclusive): an ISO date (YYYY-MM-DD), "
                    + "'today', 'yesterday' or 'N_days_ago'")
            String fromDate,

            @McpToolParam(description = "Last day to check (inclusive): an ISO date (YYYY-MM-DD), "
                    + "'today', 'yesterday' or 'N_days_ago'")
            String toDate,

            @McpToolParam(description = "The targets every day is checked against, at least one",
                    required = true)
            List<GoalTargetDTO> targets,

            @McpToolParam(description = "Whether to include the verdict for each individual day. "
                    + "Defaults to false, which keeps the response small - set it only when the "
                    + "question is about specific dates rather than about the hit rate",
                    required = false)
            Boolean includeDays) {
        LocalDate from = McpDateParser.parse(fromDate);
        LocalDate to = McpDateParser.parse(toDate);
        validate(targets);
        log.debug("MCP: checking {} goal(s) for {} to {}", targets.size(), from, to);

        List<FddbDataDTO> entries = fddbDataService.findByDateRange(from, to, false);
        long daysInRange = DAYS.between(from, to) + 1;

        if (entries.isEmpty()) {
            return GoalCheckResultDTO.builder()
                    .fromDate(from)
                    .toDate(to)
                    .daysInRange(daysInRange)
                    .daysEvaluated(0)
                    .message("No day in this range has an entry, so no goal could be checked")
                    .build();
        }

        List<GoalCheckResultDTO.DayResult> days = entries.stream()
                .map(entry -> evaluate(entry, targets))
                .toList();
        int daysMet = (int) days.stream().filter(GoalCheckResultDTO.DayResult::isMet).count();
        Streaks streaks = streaksOf(days, from, to);

        return GoalCheckResultDTO.builder()
                .fromDate(from)
                .toDate(to)
                .daysInRange(daysInRange)
                .daysEvaluated(days.size())
                .daysMet(daysMet)
                .hitRate(percentageOf(daysMet, days.size()))
                .longestStreak(streaks.longest())
                .currentStreak(streaks.current())
                .targets(perTarget(entries, targets))
                .days(Boolean.TRUE.equals(includeDays) ? days : null)
                .build();
    }

    /**
     * Loads a period and averages it, keeping the number of days the averages rest on. The entries
     * are fetched even though the averaging happens in the database: it is the only way to report
     * {@code loggedDays}, and it turns an empty period into a plain answer rather than the
     * "No data available for averaging" the averaging aggregation would raise.
     */
    private PeriodComparisonDTO.Period summarize(LocalDate from, LocalDate to) {
        List<FddbDataDTO> entries = fddbDataService.findByDateRange(from, to, false);

        StatsDTO.Averages averages = entries.isEmpty() ? null : averagesFor(from, to);

        return PeriodComparisonDTO.Period.builder()
                .fromDate(from)
                .toDate(to)
                .daysInRange(DAYS.between(from, to) + 1)
                .loggedDays(entries.size())
                .averages(averages)
                .build();
    }

    private StatsDTO.Averages averagesFor(LocalDate from, LocalDate to) {
        return fddbDataService.getRollingAverages(DateRangeDTO.builder()
                        .fromDate(from.toString())
                        .toDate(to.toString())
                        .build())
                .getAverages();
    }

    private List<PeriodComparisonDTO.MetricDelta> deltasBetween(StatsDTO.Averages a, StatsDTO.Averages b) {
        return REPORTED_METRICS.stream()
                .map(metric -> {
                    double valueA = McpMetrics.valueOf(a, metric);
                    double valueB = McpMetrics.valueOf(b, metric);
                    return PeriodComparisonDTO.MetricDelta.builder()
                            .metric(metric)
                            .unit(McpMetrics.unitOf(metric))
                            .periodA(valueA)
                            .periodB(valueB)
                            .absoluteChange(McpMetrics.roundToOneDecimal(valueA - valueB))
                            .percentageChange(valueB == 0
                                    ? null
                                    : McpMetrics.roundToOneDecimal((valueA - valueB) / valueB * 100))
                            .build();
                })
                .toList();
    }

    private GoalCheckResultDTO.DayResult evaluate(FddbDataDTO entry, List<GoalTargetDTO> targets) {
        List<GoalCheckResultDTO.MissedTarget> missed = new ArrayList<>();
        for (GoalTargetDTO target : targets) {
            double actual = McpMetrics.valueOf(entry, target.getMetric());
            if (!passes(actual, target)) {
                missed.add(GoalCheckResultDTO.MissedTarget.builder()
                        .metric(target.getMetric())
                        .comparator(target.getComparator())
                        .target(target.getValue())
                        .actual(McpMetrics.roundToOneDecimal(actual))
                        .build());
            }
        }

        return GoalCheckResultDTO.DayResult.builder()
                .date(entry.getDate())
                .met(missed.isEmpty())
                .missed(missed.isEmpty() ? null : missed)
                .build();
    }

    private List<GoalCheckResultDTO.TargetResult> perTarget(List<FddbDataDTO> entries, List<GoalTargetDTO> targets) {
        return targets.stream()
                .map(target -> {
                    int met = 0;
                    double sum = 0;
                    for (FddbDataDTO entry : entries) {
                        double actual = McpMetrics.valueOf(entry, target.getMetric());
                        sum += actual;
                        if (passes(actual, target)) {
                            met++;
                        }
                    }
                    return GoalCheckResultDTO.TargetResult.builder()
                            .metric(target.getMetric())
                            .comparator(target.getComparator())
                            .target(target.getValue())
                            .unit(McpMetrics.unitOf(target.getMetric()))
                            .daysMet(met)
                            .daysMissed(entries.size() - met)
                            .hitRate(percentageOf(met, entries.size()))
                            .average(McpMetrics.roundToOneDecimal(sum / entries.size()))
                            .build();
                })
                .toList();
    }

    private boolean passes(double actual, GoalTargetDTO target) {
        return switch (target.getComparator()) {
            case AT_LEAST -> actual >= target.getValue();
            case AT_MOST -> actual <= target.getValue();
        };
    }

    /**
     * Walks the range day by day rather than iterating the entries, so that a gap in the diary ends a
     * streak instead of being silently bridged.
     */
    private Streaks streaksOf(List<GoalCheckResultDTO.DayResult> days, LocalDate from, LocalDate to) {
        Set<LocalDate> metDates = days.stream()
                .filter(GoalCheckResultDTO.DayResult::isMet)
                .map(GoalCheckResultDTO.DayResult::getDate)
                .collect(Collectors.toSet());

        int longest = 0;
        int running = 0;
        int current = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            running = metDates.contains(date) ? running + 1 : 0;
            longest = Math.max(longest, running);
            current = running;
        }
        return new Streaks(longest, current);
    }

    private void validate(List<GoalTargetDTO> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("At least one target is required, e.g. "
                    + "{\"metric\":\"PROTEIN\",\"comparator\":\"AT_LEAST\",\"value\":120}");
        }
        for (GoalTargetDTO target : targets) {
            if (target.getMetric() == null || target.getComparator() == null) {
                throw new IllegalArgumentException("Every target needs a metric (CALORIES, FAT, CARBS, "
                        + "SUGAR, PROTEIN or FIBRE) and a comparator (AT_LEAST or AT_MOST)");
            }
        }
    }

    private double percentageOf(int part, int whole) {
        return whole == 0 ? 0.0 : McpMetrics.roundToOneDecimal((double) part / whole * 100);
    }

    /**
     * The longest run of consecutive days meeting every target, and the run the range ends on.
     */
    private record Streaks(int longest, int current) {
    }
}
