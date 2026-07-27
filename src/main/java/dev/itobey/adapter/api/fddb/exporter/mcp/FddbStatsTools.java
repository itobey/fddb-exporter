package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.*;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static java.time.temporal.ChronoUnit.*;

/**
 * MCP tools for the aggregated view of the diary.
 * <p>
 * These answer "how much on average" and "what does the whole dataset look like" without pulling
 * the underlying days into the client's context, which is what makes them worth having next to
 * {@link FddbQueryTools#getDays}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = {"fddb-exporter.mcp.enabled", "fddb-exporter.persistence.mongodb.enabled"},
        havingValue = "true")
public class FddbStatsTools {

    /**
     * Default number of extreme days. Enough to see a pattern rather than a single outlier, small
     * enough to stay a glanceable list.
     */
    private static final int DEFAULT_EXTREME_DAYS_LIMIT = 10;

    private static final int MAX_EXTREME_DAYS_LIMIT = 100;

    /**
     * Upper bound on the dates {@code list_missing_days} lists, matching the range cap of
     * {@code get_days} so the two agree on what a readable number of days is.
     * <p>
     * The cap sits here rather than in {@code StatsService.getMissingDays}: that method is also how
     * {@code export_missing_days} and the REST/UI callers learn what to repair, and they need the
     * complete list. Only the response an agent reads is bounded, and the counts next to it stay
     * exact, so nothing about the answer becomes wrong - only shorter.
     */
    private static final int MAX_MISSING_DAYS_LISTED = FddbDataService.MAX_RANGE_DAYS;

    /**
     * Upper bound on the buckets a single trend may return.
     * <p>
     * The bound is on buckets rather than on the range, because the buckets are what lands in the
     * client's context: a five-year MONTH trend is 60 rows and worth answering, while the same range
     * bucketed by DAY is ~1,800. Capping the range instead - the way {@code get_days} and
     * {@code compare_periods} do - would refuse the useful long trend along with the useless one. At
     * {@link FddbDataService#MAX_RANGE_DAYS} the DAY case ends up with exactly their cap anyway.
     */
    private static final int MAX_TREND_BUCKETS = FddbDataService.MAX_RANGE_DAYS;

    private final FddbDataService fddbDataService;

    @McpTool(
            name = "get_stats",
            description = """
                    Returns the global overview of the whole diary: how many entries exist, the date \
                    of the first and the last one, how much of that window is actually logged, the \
                    number of unique and total products, the all-time daily averages, the single \
                    highest day per nutrient and the current and longest logging streak. Call this \
                    first when a question needs to be anchored in time - it is the cheapest way to \
                    learn which period the data actually covers.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public StatsDTO getStats() {
        log.debug("MCP: retrieving global stats");
        return fddbDataService.getStats();
    }

    @McpTool(
            name = "get_averages",
            description = """
                    Returns the average daily calories, fat, carbs, sugar, protein and fibre over a \
                    date range, both bounds inclusive. Only days that have an entry are averaged, so \
                    days the user forgot to log do not drag the average down - loggedDays says how \
                    many days the averages rest on, and reading it next to daysInRange is what keeps \
                    an average over three logged days out of thirty from being reported as a month. \
                    A range with nothing logged in it comes back with found false and no averages, \
                    which is an answer, not a failure.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public AveragesResultDTO getAverages(
            @McpToolParam(description = "First day of the range (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = true)
            String fromDate,

            @McpToolParam(description = "Last day of the range (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = true)
            String toDate) {
        LocalDate from = McpDateParser.parse(fromDate);
        LocalDate to = McpDateParser.parse(toDate);
        log.debug("MCP: retrieving averages for {} to {}", from, to);

        long daysInRange = daysBetween(from, to);
        long loggedDays = fddbDataService.countByDateRange(from, to);
        AveragesResultDTO.AveragesResultDTOBuilder result = AveragesResultDTO.builder()
                .fromDate(from)
                .toDate(to)
                .daysInRange(daysInRange)
                .loggedDays(loggedDays);

        if (loggedDays == 0) {
            return result.found(false)
                    .message(nothingLoggedMessage(from, to, "average"))
                    .build();
        }

        return result.found(true)
                .averages(fddbDataService.getRollingAverages(DateRangeDTO.builder()
                                .fromDate(from.toString())
                                .toDate(to.toString())
                                .build())
                        .getAverages())
                .build();
    }

    @McpTool(
            name = "get_extreme_days",
            description = """
                    Returns the days with the highest or lowest value of one nutrient, most extreme \
                    first, optionally restricted to a date range. Use this for "which were my \
                    heaviest days" or "when did I eat the least protein" instead of pulling the range \
                    and sorting it. Only the date and the value of that one nutrient come back - call \
                    get_day for a day found here to see what was actually eaten on it. Check the \
                    'truncated' flag before calling this list "the" extremes: when it is true, more \
                    days exist past the cut and the next one may be a hair behind the last one \
                    returned.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ExtremeDaysResultDTO getExtremeDays(
            @McpToolParam(description = "The nutrient to rank the days by: CALORIES, FAT, CARBS, "
                    + "SUGAR, PROTEIN or FIBRE")
            NutrientMetric metric,

            @McpToolParam(description = "HIGHEST for the biggest days, LOWEST for the smallest. "
                    + "Defaults to HIGHEST", required = false)
            ExtremeDirection direction,

            @McpToolParam(description = "How many days to return, at most 100. Defaults to 10",
                    required = false)
            Integer limit,

            @McpToolParam(description = "Optional first day to consider (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS + ". Omit to search the whole diary",
                    required = false)
            String fromDate,

            @McpToolParam(description = "Optional last day to consider (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS + ". Omit to search the whole diary",
                    required = false)
            String toDate) {
        LocalDate from = McpDateParser.parseOptional(fromDate);
        LocalDate to = McpDateParser.parseOptional(toDate);
        ExtremeDirection effectiveDirection = direction == null ? ExtremeDirection.HIGHEST : direction;
        int effectiveLimit = boundedLimit(limit, DEFAULT_EXTREME_DAYS_LIMIT, MAX_EXTREME_DAYS_LIMIT);
        log.debug("MCP: retrieving the {} {} days for {} in {} to {}",
                effectiveLimit, effectiveDirection, metric, from, to);

        // one more than asked for, so an overflow can be reported instead of silently truncating
        List<StatsDTO.DayStats> ranked =
                fddbDataService.getExtremeDays(metric, effectiveDirection, effectiveLimit + 1, from, to);
        boolean truncated = ranked.size() > effectiveLimit;
        List<StatsDTO.DayStats> days = truncated ? ranked.subList(0, effectiveLimit) : ranked;

        return ExtremeDaysResultDTO.builder()
                .metric(metric)
                .direction(effectiveDirection)
                .unit(McpMetrics.unitOf(metric))
                .fromDate(from)
                .toDate(to)
                .resultCount(days.size())
                .limit(effectiveLimit)
                .truncated(truncated)
                .days(days)
                .build();
    }

    @McpTool(
            name = "get_trend",
            description = """
                    Returns one nutrient over time as a series of buckets, each with the average and \
                    the summed value of the days inside it. Use WEEK or MONTH granularity to answer \
                    "am I trending up?" over a long range - DAY granularity on a long range is just \
                    get_days with extra steps. At most 366 buckets are returned, so a range that would \
                    produce more is rejected: coarsen the granularity rather than narrowing the range \
                    when a long trend is what is wanted. Buckets without a single logged day are \
                    omitted rather than reported as zero, so always read dayCount before comparing two \
                    buckets: a week with two logged days is not comparable to a full one.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public TrendResultDTO getTrend(
            @McpToolParam(description = "The nutrient to trend: CALORIES, FAT, CARBS, SUGAR, PROTEIN "
                    + "or FIBRE")
            NutrientMetric metric,

            @McpToolParam(description = "First day of the range (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = true)
            String fromDate,

            @McpToolParam(description = "Last day of the range (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = true)
            String toDate,

            @McpToolParam(description = "Bucket size: DAY, WEEK (ISO weeks, Monday to Sunday) or "
                    + "MONTH. Defaults to WEEK", required = false)
            TrendGranularity granularity) {
        LocalDate from = McpDateParser.parse(fromDate);
        LocalDate to = McpDateParser.parse(toDate);
        TrendGranularity effectiveGranularity = granularity == null ? TrendGranularity.WEEK : granularity;
        checkBucketCount(from, to, effectiveGranularity);
        log.debug("MCP: retrieving the {} trend of {} for {} to {}", effectiveGranularity, metric, from, to);

        List<TrendPointDTO> buckets = fddbDataService.getTrend(metric, from, to, effectiveGranularity);

        return TrendResultDTO.builder()
                .metric(metric)
                .granularity(effectiveGranularity)
                .unit(McpMetrics.unitOf(metric))
                .fromDate(from)
                .toDate(to)
                .bucketCount(buckets.size())
                .loggedDays(buckets.stream().mapToLong(TrendPointDTO::getDayCount).sum())
                .buckets(buckets)
                .build();
    }

    @McpTool(
            name = "get_weekday_breakdown",
            description = """
                    Returns the average daily nutrition grouped by day of the week - "do my weekends \
                    wreck the average?". Both bounds are optional; without them the whole diary is \
                    covered. A day of the week with no logged day at all is omitted, and dayCount \
                    says how many days each average rests on.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public WeekdayBreakdownResultDTO getWeekdayBreakdown(
            @McpToolParam(description = "Optional first day to include (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = false)
            String fromDate,

            @McpToolParam(description = "Optional last day to include (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = false)
            String toDate) {
        LocalDate from = McpDateParser.parseOptional(fromDate);
        LocalDate to = McpDateParser.parseOptional(toDate);
        log.debug("MCP: retrieving the weekday breakdown for {} to {}", from, to);

        List<WeekdayStatsDTO> weekdays = fddbDataService.getWeekdayBreakdown(from, to);

        return WeekdayBreakdownResultDTO.builder()
                .fromDate(from)
                .toDate(to)
                .loggedDays(weekdays.stream().mapToLong(WeekdayStatsDTO::getDayCount).sum())
                .weekdays(weekdays)
                .build();
    }

    @McpTool(
            name = "get_macro_split",
            description = """
                    Returns the share of energy coming from fat, carbs and protein over a range. \
                    The split is kcal-weighted, not gram-weighted: grams are converted with the \
                    Atwater factors (fat 9 kcal/g, carbs and protein 4 kcal/g) before the \
                    percentages are computed, which is the only way the three shares can be \
                    compared to each other. macroCalories is derived from the macros and \
                    averageCalories is what FDDB itself reports; the two normally differ by a few \
                    kcal, and the percentages are computed against macroCalories. loggedDays says \
                    how many days the split rests on, and a range with nothing logged in it comes \
                    back with found false and no split rather than as an error.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public MacroSplitResultDTO getMacroSplit(
            @McpToolParam(description = "First day of the range (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS)
            String fromDate,

            @McpToolParam(description = "Last day of the range (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS)
            String toDate) {
        LocalDate from = McpDateParser.parse(fromDate);
        LocalDate to = McpDateParser.parse(toDate);
        log.debug("MCP: retrieving the macro split for {} to {}", from, to);

        long daysInRange = daysBetween(from, to);
        long loggedDays = fddbDataService.countByDateRange(from, to);
        MacroSplitResultDTO.MacroSplitResultDTOBuilder result = MacroSplitResultDTO.builder()
                .fromDate(from)
                .toDate(to)
                .daysInRange(daysInRange)
                .loggedDays(loggedDays);

        if (loggedDays == 0) {
            return result.found(false)
                    .message(nothingLoggedMessage(from, to, "split"))
                    .build();
        }

        return result.found(true)
                .split(fddbDataService.getMacroSplit(from, to))
                .build();
    }

    @McpTool(
            name = "list_missing_days",
            description = """
                    Lists the days in a range that were never logged - "when did I forget to log?". A \
                    day with an entry but no calories at all counts as missing too, since that is \
                    what an aborted export looks like. Worth calling before drawing conclusions from \
                    averages over a long range: 20 missing days out of 30 makes any average of that \
                    month close to meaningless. The range itself is unlimited, but at most 366 dates \
                    are listed; missingCount and loggedCount always cover the whole range, so answer \
                    "how many did I miss?" from those and narrow the range if the dates themselves \
                    are needed.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public MissingDaysResultDTO listMissingDays(
            @McpToolParam(description = "First day to check (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = true)
            String fromDate,

            @McpToolParam(description = "Last day to check (inclusive): "
                    + McpDateParser.ACCEPTED_FORMATS, required = true)
            String toDate) {
        LocalDate from = McpDateParser.parse(fromDate);
        LocalDate to = McpDateParser.parse(toDate);
        long daysChecked = daysBetween(from, to);
        log.debug("MCP: retrieving the missing days for {} to {}", from, to);

        List<LocalDate> missingDays = fddbDataService.getMissingDays(from, to);
        boolean truncated = missingDays.size() > MAX_MISSING_DAYS_LISTED;

        return MissingDaysResultDTO.builder()
                .fromDate(from)
                .toDate(to)
                .daysChecked(daysChecked)
                // the counts describe the whole range even when the list below does not
                .missingCount(missingDays.size())
                .loggedCount(daysChecked - missingDays.size())
                .truncated(truncated)
                .limit(truncated ? MAX_MISSING_DAYS_LISTED : null)
                .missingDays(truncated
                        ? List.copyOf(missingDays.subList(0, MAX_MISSING_DAYS_LISTED))
                        : missingDays)
                .build();
    }

    /**
     * The length of a range in days, rejecting an inverted one before the store is touched. The
     * aggregations behind these tools would raise their own error for it, but not one worded for
     * the agent that has to correct the call.
     */
    private long daysBetween(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new DateTimeException("The 'from' date cannot be after the 'to' date");
        }
        return DAYS.between(from, to) + 1;
    }

    /**
     * Rejects a trend that would come back with more than {@link #MAX_TREND_BUCKETS} buckets, before
     * the store is touched.
     * <p>
     * Counts the buckets the range spans rather than the ones that will actually be filled: empty
     * buckets are omitted from the response, so the span is an upper bound, and one that can be
     * computed without loading anything. The message names both ways out, since coarsening the
     * granularity is usually the one the caller wants and narrowing the range is the one it would
     * otherwise guess.
     */
    private void checkBucketCount(LocalDate from, LocalDate to, TrendGranularity granularity) {
        long days = daysBetween(from, to);
        long buckets = switch (granularity) {
            case DAY -> days;
            // from the Monday of the first ISO week / the first of the first month, so a range
            // starting mid-bucket still counts that bucket
            case WEEK -> WEEKS.between(from.with(DayOfWeek.MONDAY), to) + 1;
            case MONTH -> MONTHS.between(from.withDayOfMonth(1), to) + 1;
        };

        if (buckets > MAX_TREND_BUCKETS) {
            throw new DateTimeException("A trend must not exceed " + MAX_TREND_BUCKETS + " buckets, but "
                    + granularity + " granularity over " + from + " to " + to + " would produce "
                    + buckets + " - please use a coarser granularity or narrow the range");
        }
    }

    /**
     * The answer for an empty range. Worded as a finding rather than a failure: an agent that reads
     * "no data available" retries or apologises, where "nothing was logged" is the thing to report.
     */
    private String nothingLoggedMessage(LocalDate from, LocalDate to, String verb) {
        return "No day between " + from + " and " + to + " has an entry, so there is nothing to "
                + verb + " - the user logged nothing in this range.";
    }

    private int boundedLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }
}
