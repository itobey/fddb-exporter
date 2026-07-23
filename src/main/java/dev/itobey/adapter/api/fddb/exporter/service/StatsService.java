package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.ExtremeDirection;
import dev.itobey.adapter.api.fddb.exporter.dto.MacroSplitDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.WeekdayStatsDTO;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
public class StatsService {

    public static final String COLLECTION_NAME = "fddb";

    private static final double KCAL_PER_GRAM_FAT = 9.0;
    private static final double KCAL_PER_GRAM_CARBS = 4.0;
    private static final double KCAL_PER_GRAM_PROTEIN = 4.0;

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    public StatsDTO getStats() {
        long amountEntries = getAmountEntries();

        // Handle empty database - return stats with zero/null values
        if (amountEntries == 0) {
            return StatsDTO.builder()
                    .amountEntries(0L)
                    .firstEntryDate(null)
                    .mostRecentMissingDay(null)
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

        LocalDate firstEntryDate = getFirstEntryDate();
        double entryPercentage = roundToOneDecimal(calculateEntryPercentage(firstEntryDate, amountEntries));
        StatsDTO.Averages averageTotals = roundAverages(getAverageTotals());
        long uniqueProducts = getUniqueProductsCount();
        long totalProducts = getTotalProductsCount();

        return StatsDTO.builder()
                .amountEntries(amountEntries)
                .firstEntryDate(firstEntryDate)
                .mostRecentMissingDay(getMostRecentMissingDay())
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

    private long getAmountEntries() {
        return mongoTemplate.count(new Query(), COLLECTION_NAME);
    }

    private LocalDate getFirstEntryDate() {
        requireMongoTemplate();
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "date")).limit(1);
        FddbData firstDocument = mongoTemplate.findOne(query, FddbData.class, COLLECTION_NAME);
        if (firstDocument == null) {
            return null;
        }
        return firstDocument.getDate();
    }

    private StatsDTO.Averages getAverageTotals() {
        return getAverages(null);
    }

    public StatsDTO.Averages getAveragesForDateRange(LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);

        Criteria criteria = Criteria.where("date").gte(fromDate).lte(toDate);
        return roundAverages(getAverages(criteria));
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
        requireMongoTemplate();
        validateDateRange(fromDate, toDate);

        Sort.Direction sortDirection = direction == ExtremeDirection.LOWEST ? Sort.Direction.ASC : Sort.Direction.DESC;

        List<AggregationOperation> operations = new ArrayList<>();
        Criteria criteria = buildDateCriteria(fromDate, toDate);
        if (criteria != null) {
            operations.add(match(criteria));
        }
        operations.add(sort(sortDirection, metric.getFieldName()));
        operations.add(limit(limit));
        operations.add(project("date").and(metric.getFieldName()).as("total"));

        AggregationResults<StatsDTO.DayStats> results =
                mongoTemplate.aggregate(newAggregation(operations), COLLECTION_NAME, StatsDTO.DayStats.class);

        return results.getMappedResults().stream()
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
        validateDateRange(fromDate, toDate);

        ToDoubleFunction<FddbData> valueOf = metricAccessor(metric);
        Map<String, List<FddbData>> buckets = new LinkedHashMap<>();
        for (FddbData entry : findDailyTotals(fromDate, toDate)) {
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
                    .total(roundToOneDecimal(total))
                    .average(roundToOneDecimal(total / entries.size()))
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
        validateDateRange(fromDate, toDate);

        Map<DayOfWeek, List<FddbData>> byWeekday = new EnumMap<>(DayOfWeek.class);
        for (FddbData entry : findDailyTotals(fromDate, toDate)) {
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
                .fatCalories(roundToOneDecimal(fatCalories))
                .carbsCalories(roundToOneDecimal(carbsCalories))
                .proteinCalories(roundToOneDecimal(proteinCalories))
                .macroCalories(roundToOneDecimal(macroCalories))
                .averageCalories(averages.getAvgTotalCalories())
                .fatPercentage(percentageOf(fatCalories, macroCalories))
                .carbsPercentage(percentageOf(carbsCalories, macroCalories))
                .proteinPercentage(percentageOf(proteinCalories, macroCalories))
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
        validateDateRange(fromDate, toDate);
        requireMongoTemplate();

        Query query = new Query(Criteria.where("date").gte(fromDate).lte(toDate).and("totalCalories").gt(0));
        query.fields().exclude("products");
        List<FddbData> loggedDays = mongoTemplate.find(query, FddbData.class, COLLECTION_NAME);

        java.util.Set<LocalDate> loggedDates = new java.util.HashSet<>();
        for (FddbData entry : loggedDays) {
            loggedDates.add(entry.getDate());
        }

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

    private StatsDTO.Averages getAverages(Criteria criteria) {
        requireMongoTemplate();

        List<AggregationOperation> operations = new ArrayList<>();

        if (criteria != null) {
            operations.add(match(criteria));
        }

        operations.add(group()
                .avg("totalCalories").as("avgTotalCalories")
                .avg("totalFat").as("avgTotalFat")
                .avg("totalCarbs").as("avgTotalCarbs")
                .avg("totalSugar").as("avgTotalSugar")
                .avg("totalProtein").as("avgTotalProtein")
                .avg("totalFibre").as("avgTotalFibre")
        );

        Aggregation aggregation = newAggregation(operations);
        AggregationResults<StatsDTO.Averages> results = mongoTemplate.aggregate(aggregation, COLLECTION_NAME, StatsDTO.Averages.class);

        StatsDTO.Averages averages = results.getUniqueMappedResult();
        if (averages == null) {
            throw new IllegalStateException("No data available for averaging");
        }

        return averages;
    }


    private long getUniqueProductsCount() {
        Aggregation aggregation = newAggregation(
                unwind("products"),
                group("products.name"),
                count().as("uniqueCount")
        );
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Document.class);
        Document doc = results.getUniqueMappedResult();
        if (doc == null) {
            return 0L;
        }
        Object val = doc.get("uniqueCount");
        return val instanceof Number ? ((Number) val).longValue() : 0L;
    }

    private long getTotalProductsCount() {
        Aggregation aggregation = newAggregation(
                unwind("products"),
                count().as("totalCount")
        );
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Document.class);
        Document doc = results.getUniqueMappedResult();
        if (doc == null) {
            return 0L;
        }
        Object val = doc.get("totalCount");
        return val instanceof Number ? ((Number) val).longValue() : 0L;
    }

    private double calculateEntryPercentage(LocalDate givenDate, long documentCount) {
        long daysSince = ChronoUnit.DAYS.between(givenDate, LocalDate.now());
        return (double) documentCount / daysSince * 100;
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double percentageOf(double part, double whole) {
        return whole == 0 ? 0.0 : roundToOneDecimal(part / whole * 100);
    }

    private StatsDTO.Averages roundAverages(StatsDTO.Averages averages) {
        return StatsDTO.Averages.builder()
                .avgTotalCalories(roundToOneDecimal(averages.getAvgTotalCalories()))
                .avgTotalFat(roundToOneDecimal(averages.getAvgTotalFat()))
                .avgTotalCarbs(roundToOneDecimal(averages.getAvgTotalCarbs()))
                .avgTotalSugar(roundToOneDecimal(averages.getAvgTotalSugar()))
                .avgTotalProtein(roundToOneDecimal(averages.getAvgTotalProtein()))
                .avgTotalFibre(roundToOneDecimal(averages.getAvgTotalFibre()))
                .build();
    }

    private StatsDTO.DayStats roundDayStats(StatsDTO.DayStats dayStats) {
        if (dayStats == null) {
            return null;
        }
        return StatsDTO.DayStats.builder()
                .date(dayStats.getDate())
                .total(roundToOneDecimal(dayStats.getTotal()))
                .build();
    }

    private Object getMostRecentMissingDay() {
        if (mongoTemplate == null) {
            return "only available with MongoDB";
        }

        try {
            LocalDate firstEntryDate = getFirstEntryDate();

            // Handle empty database
            if (firstEntryDate == null) {
                return null;
            }

            LocalDate yesterday = LocalDate.now().minusDays(1);
            if (yesterday.isBefore(firstEntryDate)) {
                return null;
            }

            List<LocalDate> missingDays = getMissingDays(firstEntryDate, yesterday);
            return missingDays.isEmpty() ? null : missingDays.getLast();
        } catch (Exception e) {
            return "only available with MongoDB";
        }
    }

    /**
     * Loads the daily totals for a range without the products array, which keeps the in-memory
     * grouping below cheap even for multi-year ranges.
     */
    private List<FddbData> findDailyTotals(LocalDate fromDate, LocalDate toDate) {
        requireMongoTemplate();

        Query query = new Query();
        Criteria criteria = buildDateCriteria(fromDate, toDate);
        if (criteria != null) {
            query.addCriteria(criteria);
        }
        query.fields().exclude("products");
        query.with(Sort.by(Sort.Direction.ASC, "date"));

        return mongoTemplate.find(query, FddbData.class, COLLECTION_NAME);
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
        return roundToOneDecimal(entries.stream().mapToDouble(accessor).average().orElse(0.0));
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

    private Criteria buildDateCriteria(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return null;
        }
        Criteria criteria = Criteria.where("date");
        if (fromDate != null) {
            criteria = criteria.gte(fromDate);
        }
        if (toDate != null) {
            criteria = criteria.lte(toDate);
        }
        return criteria;
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("The 'from' date cannot be after the 'to' date");
        }
    }

    private void requireMongoTemplate() {
        if (mongoTemplate == null) {
            throw new IllegalStateException("MongoDB is not configured");
        }
    }
}
