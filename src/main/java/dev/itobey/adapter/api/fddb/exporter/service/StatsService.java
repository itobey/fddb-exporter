package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.StatsRepository;
import dev.itobey.adapter.api.fddb.exporter.util.DateRanges;
import dev.itobey.adapter.api.fddb.exporter.util.Rounding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Computes statistics from {@link StatsRepository} query results.
 */
@Slf4j
@Service
public class StatsService {

    private static final double KCAL_PER_GRAM_FAT = 9.0;
    private static final double KCAL_PER_GRAM_CARBS = 4.0;
    private static final double KCAL_PER_GRAM_PROTEIN = 4.0;

    /**
     * Null when MongoDB persistence is disabled.
     */
    private final StatsRepository statsRepository;

    public StatsService(ObjectProvider<StatsRepository> statsRepository) {
        this.statsRepository = statsRepository.getIfAvailable();
    }

    public StatsDTO getStats() {
        requireRepository();
        long amountEntries = statsRepository.countAll();

        // Handle empty database - return stats with zero/null values
        if (amountEntries == 0) {
            return StatsDTO.builder()
                    .amountEntries(0L)
                    .firstEntryDate(null)
                    .lastEntryDate(null)
                    .mostRecentMissingDay(null)
                    .missingDaysCount(0L)
                    .currentStreak(0)
                    .longestStreak(0)
                    .entryPercentage(0.0)
                    .uniqueProducts(0L)
                    .totalProducts(0L)
                    .averageTotals(null)
                    .highestCaloriesDay(null)
                    .highestFatDay(null)
                    .highestCarbsDay(null)
                    .highestProteinDay(null)
                    .highestFibreDay(null)
                    .highestSugarDay(null)
                    .build();
        }

        LocalDate firstEntryDate = statsRepository.findFirstEntryDate();
        LocalDate lastEntryDate = statsRepository.findLastEntryDate();
        double entryPercentage = Rounding.toOneDecimal(calculateEntryPercentage(firstEntryDate, amountEntries));
        StatsDTO.Averages averageTotals = roundAverages(requireAverages(null, null));
        long uniqueProducts = statsRepository.countUniqueProducts();
        long totalProducts = statsRepository.countTotalProducts();
        Coverage coverage = getCoverage(firstEntryDate);

        return StatsDTO.builder()
                .amountEntries(amountEntries)
                .firstEntryDate(firstEntryDate)
                .lastEntryDate(lastEntryDate)
                .mostRecentMissingDay(coverage.mostRecentMissingDay())
                .missingDaysCount(coverage.missingDaysCount())
                .currentStreak(coverage.currentStreak())
                .longestStreak(coverage.longestStreak())
                .entryPercentage(entryPercentage)
                .uniqueProducts(uniqueProducts)
                .totalProducts(totalProducts)
                .highestCaloriesDay(getDayWithHighestTotal(NutrientMetric.CALORIES))
                .highestFatDay(getDayWithHighestTotal(NutrientMetric.FAT))
                .highestCarbsDay(getDayWithHighestTotal(NutrientMetric.CARBS))
                .highestProteinDay(getDayWithHighestTotal(NutrientMetric.PROTEIN))
                .highestFibreDay(getDayWithHighestTotal(NutrientMetric.FIBRE))
                .highestSugarDay(getDayWithHighestTotal(NutrientMetric.SUGAR))
                .averageTotals(averageTotals)
                .build();
    }

    /**
     * Returns just the period the diary covers: the number of entries and the dates of the first
     * and the last one.
     * <p>
     * The narrow alternative to {@link #getStats()} for a caller that wants nothing else. Stats
     * computes per-nutrient extremes, unique-product counts and both streaks over the entire
     * collection, which is a multi-second aggregation on a diary of several years; this is a count
     * and two indexed lookups.
     *
     * @return the coverage window, with a zero count and null dates when the diary is empty
     */
    public CoverageWindowDTO getCoverageWindow() {
        requireRepository();
        long amountEntries = statsRepository.countAll();

        if (amountEntries == 0) {
            return CoverageWindowDTO.builder().entryCount(0).build();
        }

        return CoverageWindowDTO.builder()
                .entryCount(amountEntries)
                .firstEntryDate(statsRepository.findFirstEntryDate())
                .lastEntryDate(statsRepository.findLastEntryDate())
                .build();
    }

    public StatsDTO.Averages getAveragesForDateRange(LocalDate fromDate, LocalDate toDate) {
        DateRanges.validate(fromDate, toDate);
        requireRepository();

        return roundAverages(requireAverages(fromDate, toDate));
    }

    /**
     * Counts the entries in a date range, both bounds inclusive and either of them optional.
     * <p>
     * This is the denominator of {@link #getAveragesForDateRange}: how many days its numbers rest
     * on, and — when it is zero — the cheap way to know beforehand that the averaging aggregation
     * would have nothing to average.
     *
     * @param fromDate the first date to count, or null for no lower bound
     * @param toDate   the last date to count, or null for no upper bound
     * @return the number of days in the range that have an entry
     */
    public long countByDateRange(LocalDate fromDate, LocalDate toDate) {
        DateRanges.validate(fromDate, toDate);
        requireRepository();

        return statsRepository.countInRange(fromDate, toDate);
    }

    /**
     * Returns the top or bottom N days for a metric, optionally scoped to a date range.
     * This is the range-aware generalisation of the all-time, single-day extremes reported by
     * {@link #getStats()}.
     *
     * @param metric    the metric to rank days by
     * @param direction whether the highest or the lowest days are wanted
     * @param limit     the maximum number of days to return
     * @param fromDate  the earliest date to include, or null for no lower bound
     * @param toDate    the latest date to include, or null for no upper bound
     * @return the matching days with their value for the metric, most extreme first
     */
    public List<StatsDTO.DayStats> getExtremeDays(NutrientMetric metric, ExtremeDirection direction, int limit,
                                                  LocalDate fromDate, LocalDate toDate) {
        DateRanges.validate(fromDate, toDate);
        requireRepository();

        return statsRepository.findExtremeDays(metric, direction, limit, fromDate, toDate).stream()
                .map(this::roundDayStats)
                .toList();
    }

    /**
     * Builds a time series of one metric over a date range, bucketed by day, ISO week or month.
     * Buckets without a single entry are omitted rather than reported as zero, so an average
     * never gets dragged down by days that were simply not logged.
     *
     * @param metric      the metric to trend
     * @param fromDate    the first date to include
     * @param toDate      the last date to include
     * @param granularity the bucket size
     * @return the buckets in chronological order
     */
    public List<TrendPointDTO> getTrend(NutrientMetric metric, LocalDate fromDate, LocalDate toDate,
                                        TrendGranularity granularity) {
        DateRanges.validate(fromDate, toDate);
        requireRepository();

        ToDoubleFunction<FddbData> valueOf = metricAccessor(metric);
        Map<String, List<FddbData>> buckets = new LinkedHashMap<>();
        for (FddbData entry : statsRepository.findDailyTotals(fromDate, toDate)) {
            buckets.computeIfAbsent(bucketLabel(entry.getDate(), granularity), key -> new ArrayList<>()).add(entry);
        }

        List<TrendPointDTO> trend = new ArrayList<>();
        buckets.forEach((label, entries) -> {
            double total = entries.stream().mapToDouble(valueOf).sum();
            trend.add(TrendPointDTO.builder()
                    .bucket(label)
                    .fromDate(entries.getFirst().getDate())
                    .toDate(entries.getLast().getDate())
                    .dayCount(entries.size())
                    .total(Rounding.toOneDecimal(total))
                    .average(Rounding.toOneDecimal(total / entries.size()))
                    .build());
        });
        return trend;
    }

    /**
     * Averages the daily totals grouped by day of the week, so weekday and weekend patterns
     * become visible. Days of the week without a single entry are omitted.
     *
     * @param fromDate the earliest date to include, or null for no lower bound
     * @param toDate   the latest date to include, or null for no upper bound
     * @return the averages per day of the week, Monday first
     */
    public List<WeekdayStatsDTO> getWeekdayBreakdown(LocalDate fromDate, LocalDate toDate) {
        DateRanges.validate(fromDate, toDate);
        requireRepository();

        Map<DayOfWeek, List<FddbData>> byWeekday = new EnumMap<>(DayOfWeek.class);
        for (FddbData entry : statsRepository.findDailyTotals(fromDate, toDate)) {
            byWeekday.computeIfAbsent(entry.getDate().getDayOfWeek(), key -> new ArrayList<>()).add(entry);
        }

        return byWeekday.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(DayOfWeek::getValue)))
                .map(entry -> WeekdayStatsDTO.builder()
                        .dayOfWeek(entry.getKey())
                        .dayCount(entry.getValue().size())
                        .averages(averageOf(entry.getValue()))
                        .build())
                .toList();
    }

    /**
     * Computes the share of energy coming from fat, carbs and protein over a date range.
     * The split is kcal-weighted (fat 9 kcal/g, carbs and protein 4 kcal/g), not gram-weighted.
     *
     * @param fromDate the first date to include
     * @param toDate   the last date to include
     * @return the macro split for the range
     */
    public MacroSplitDTO getMacroSplit(LocalDate fromDate, LocalDate toDate) {
        StatsDTO.Averages averages = getAveragesForDateRange(fromDate, toDate);

        double fatCalories = averages.getAvgTotalFat() * KCAL_PER_GRAM_FAT;
        double carbsCalories = averages.getAvgTotalCarbs() * KCAL_PER_GRAM_CARBS;
        double proteinCalories = averages.getAvgTotalProtein() * KCAL_PER_GRAM_PROTEIN;
        double macroCalories = fatCalories + carbsCalories + proteinCalories;

        return MacroSplitDTO.builder()
                .fromDate(fromDate.toString())
                .toDate(toDate.toString())
                .fatCalories(Rounding.toOneDecimal(fatCalories))
                .carbsCalories(Rounding.toOneDecimal(carbsCalories))
                .proteinCalories(Rounding.toOneDecimal(proteinCalories))
                .macroCalories(Rounding.toOneDecimal(macroCalories))
                .averageCalories(averages.getAvgTotalCalories())
                .fatPercentage(Rounding.percentageOf(fatCalories, macroCalories))
                .carbsPercentage(Rounding.percentageOf(carbsCalories, macroCalories))
                .proteinPercentage(Rounding.percentageOf(proteinCalories, macroCalories))
                .build();
    }

    /**
     * Lists every day in the range that has no entry at all or an entry without a single calorie —
     * the generalisation of {@code mostRecentMissingDay} from a single date to the full gap list.
     *
     * @param fromDate the first date to check
     * @param toDate   the last date to check
     * @return the missing days in chronological order
     */
    public List<LocalDate> getMissingDays(LocalDate fromDate, LocalDate toDate) {
        DateRanges.validate(fromDate, toDate);
        requireRepository();

        return collectMissingDays(statsRepository.findLoggedDates(fromDate, toDate), fromDate, toDate);
    }

    private List<LocalDate> collectMissingDays(Set<LocalDate> loggedDates, LocalDate fromDate, LocalDate toDate) {
        List<LocalDate> missingDays = new ArrayList<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            if (!loggedDates.contains(date)) {
                missingDays.add(date);
            }
        }
        return missingDays;
    }

    private StatsDTO.DayStats getDayWithHighestTotal(NutrientMetric metric) {
        List<StatsDTO.DayStats> extremes = getExtremeDays(metric, ExtremeDirection.HIGHEST, 1, null, null);
        return extremes.isEmpty() ? null : extremes.getFirst();
    }

    /**
     * Rejects empty ranges with HTTP 400.
     */
    private StatsDTO.Averages requireAverages(LocalDate fromDate, LocalDate toDate) {
        return statsRepository.averages(fromDate, toDate)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No entries in the requested range, so there is nothing to average - pick a range that has data"));
    }

    /**
     * The share of days since the first entry that actually have one.
     * <p>
     * The range is inclusive at both ends, so a diary whose first entry is today covers exactly one day
     * rather than zero - dividing by zero here produced {@code Infinity}, which is not valid JSON and
     * rendered as "Infinity%" on the dashboard of every brand-new installation. Capped at 100 because
     * duplicate days would otherwise be able to push it above.
     */
    private double calculateEntryPercentage(LocalDate givenDate, long documentCount) {
        long daysSince = ChronoUnit.DAYS.between(givenDate, LocalDate.now()) + 1;
        if (daysSince <= 0) {
            return 0.0;
        }
        return Math.min(100.0, (double) documentCount / daysSince * 100);
    }

    private StatsDTO.Averages roundAverages(StatsDTO.Averages averages) {
        return StatsDTO.Averages.builder()
                .avgTotalCalories(Rounding.toOneDecimal(averages.getAvgTotalCalories()))
                .avgTotalFat(Rounding.toOneDecimal(averages.getAvgTotalFat()))
                .avgTotalCarbs(Rounding.toOneDecimal(averages.getAvgTotalCarbs()))
                .avgTotalSugar(Rounding.toOneDecimal(averages.getAvgTotalSugar()))
                .avgTotalProtein(Rounding.toOneDecimal(averages.getAvgTotalProtein()))
                .avgTotalFibre(Rounding.toOneDecimal(averages.getAvgTotalFibre()))
                .build();
    }

    private StatsDTO.DayStats roundDayStats(StatsDTO.DayStats dayStats) {
        if (dayStats == null) {
            return null;
        }
        return StatsDTO.DayStats.builder()
                .date(dayStats.getDate())
                .total(Rounding.toOneDecimal(dayStats.getTotal()))
                .build();
    }

    /**
     * Walks the logged and unlogged days between the first entry and today a single time, so the
     * overall statistics can report the most recent gap, the total number of gaps and both streaks
     * from one query.
     *
     * @param firstEntryDate the date of the first entry, or null for an empty database
     * @return the coverage figures, with null values if they cannot be determined
     */
    private Coverage getCoverage(LocalDate firstEntryDate) {
        if (statsRepository == null) {
            return Coverage.unknown();
        }

        try {
            // Handle empty database
            if (firstEntryDate == null) {
                return Coverage.unknown();
            }

            LocalDate today = LocalDate.now();
            Set<LocalDate> loggedDates = statsRepository.findLoggedDates(firstEntryDate, today);

            // today is deliberately left out of the gaps: a day still in progress is not a missing day
            LocalDate lastCompleteDay = today.minusDays(1);
            List<LocalDate> missingDays = lastCompleteDay.isBefore(firstEntryDate)
                    ? List.of()
                    : collectMissingDays(loggedDates, firstEntryDate, lastCompleteDay);

            return new Coverage(
                    missingDays.isEmpty() ? null : missingDays.getLast(),
                    (long) missingDays.size(),
                    currentStreak(loggedDates, firstEntryDate, today),
                    longestStreak(loggedDates, firstEntryDate, today));
        } catch (DataAccessException e) {
            // the diary itself is still worth reporting, so a failing coverage query degrades to
            // null values rather than failing the whole stats call - but it must not do so silently
            log.warn("Could not compute coverage", e);
            return Coverage.unknown();
        }
    }

    /**
     * Counts the logged days in a row up to now. Today only counts once it has an entry, but its
     * absence does not end the streak either - otherwise every streak would look broken until the
     * scheduler runs.
     */
    private int currentStreak(Set<LocalDate> loggedDates, LocalDate firstEntryDate, LocalDate today) {
        int streak = 0;
        for (LocalDate date = loggedDates.contains(today) ? today : today.minusDays(1);
             !date.isBefore(firstEntryDate) && loggedDates.contains(date);
             date = date.minusDays(1)) {
            streak++;
        }
        return streak;
    }

    private int longestStreak(Set<LocalDate> loggedDates, LocalDate firstEntryDate, LocalDate today) {
        int longest = 0;
        int running = 0;
        for (LocalDate date = firstEntryDate; !date.isAfter(today); date = date.plusDays(1)) {
            running = loggedDates.contains(date) ? running + 1 : 0;
            longest = Math.max(longest, running);
        }
        return longest;
    }

    /**
     * How completely the diary is filled in: the most recent gap, the number of gaps and the
     * streaks. Every value is null when it could not be determined; {@code mostRecentMissingDay}
     * is also null when there simply is no gap, which {@code missingDaysCount} tells apart - it is
     * {@code 0} for a gapless diary and null for figures that could not be computed.
     */
    private record Coverage(LocalDate mostRecentMissingDay, Long missingDaysCount, Integer currentStreak,
                            Integer longestStreak) {

        private static Coverage unknown() {
            return new Coverage(null, null, null, null);
        }
    }

    private StatsDTO.Averages averageOf(List<FddbData> entries) {
        return StatsDTO.Averages.builder()
                .avgTotalCalories(averageOf(entries, FddbData::getTotalCalories))
                .avgTotalFat(averageOf(entries, FddbData::getTotalFat))
                .avgTotalCarbs(averageOf(entries, FddbData::getTotalCarbs))
                .avgTotalSugar(averageOf(entries, FddbData::getTotalSugar))
                .avgTotalProtein(averageOf(entries, FddbData::getTotalProtein))
                .avgTotalFibre(averageOf(entries, FddbData::getTotalFibre))
                .build();
    }

    private double averageOf(List<FddbData> entries, ToDoubleFunction<FddbData> accessor) {
        return Rounding.toOneDecimal(entries.stream().mapToDouble(accessor).average().orElse(0.0));
    }

    private ToDoubleFunction<FddbData> metricAccessor(NutrientMetric metric) {
        return switch (metric) {
            case CALORIES -> FddbData::getTotalCalories;
            case FAT -> FddbData::getTotalFat;
            case CARBS -> FddbData::getTotalCarbs;
            case SUGAR -> FddbData::getTotalSugar;
            case PROTEIN -> FddbData::getTotalProtein;
            case FIBRE -> FddbData::getTotalFibre;
        };
    }

    private String bucketLabel(LocalDate date, TrendGranularity granularity) {
        return switch (granularity) {
            case DAY -> date.toString();
            case WEEK -> "%d-W%02d".formatted(
                    date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTH -> YearMonth.from(date).toString();
        };
    }

    private void requireRepository() {
        if (statsRepository == null) {
            throw new IllegalStateException("MongoDB is not configured");
        }
    }
}
