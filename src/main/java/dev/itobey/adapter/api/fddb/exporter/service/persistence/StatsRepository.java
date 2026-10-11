package dev.itobey.adapter.api.fddb.exporter.service.persistence;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.ExtremeDirection;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;

import static dev.itobey.adapter.api.fddb.exporter.service.persistence.MongoQueries.COLLECTION_NAME;
import static dev.itobey.adapter.api.fddb.exporter.service.persistence.MongoQueries.buildDateCriteria;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * MongoDB queries for statistics. Available only when MongoDB persistence is enabled.
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
public class StatsRepository {

    private final MongoTemplate mongoTemplate;

    /**
     * @return the total number of entries in the diary
     */
    public long countAll() {
        return mongoTemplate.count(new Query(), COLLECTION_NAME);
    }

    /**
     * Counts the entries in a date range without loading them, both bounds inclusive and optional.
     *
     * @param fromDate the first date to count, or null for no lower bound
     * @param toDate   the last date to count, or null for no upper bound
     * @return the number of days in the range that have an entry
     */
    public long countInRange(LocalDate fromDate, LocalDate toDate) {
        Criteria criteria = buildDateCriteria(fromDate, toDate);
        Query query = criteria == null ? new Query() : new Query(criteria);
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    /**
     * @return the date of the oldest entry, or null for an empty diary
     */
    public LocalDate findFirstEntryDate() {
        return findEntryDateAtEdge(Sort.Direction.ASC);
    }

    /**
     * @return the date of the newest entry, or null for an empty diary
     */
    public LocalDate findLastEntryDate() {
        return findEntryDateAtEdge(Sort.Direction.DESC);
    }

    private LocalDate findEntryDateAtEdge(Sort.Direction direction) {
        Query query = new Query().with(Sort.by(direction, "date")).limit(1);
        query.fields().exclude("products");
        FddbData document = mongoTemplate.findOne(query, FddbData.class, COLLECTION_NAME);
        return document == null ? null : document.getDate();
    }

    /**
     * Averages the six daily totals over a date range, both bounds inclusive and optional.
     *
     * @param fromDate the first date to include, or null for no lower bound
     * @param toDate   the last date to include, or null for no upper bound
     * @return the averages, or empty if the range holds no entry at all
     */
    public Optional<StatsDTO.Averages> averages(LocalDate fromDate, LocalDate toDate) {
        List<AggregationOperation> operations = new ArrayList<>();

        Criteria criteria = buildDateCriteria(fromDate, toDate);
        if (criteria != null) {
            operations.add(match(criteria));
        }

        operations.add(group()
                .avg("totalCalories").as("avgTotalCalories")
                .avg("totalFat").as("avgTotalFat")
                .avg("totalCarbs").as("avgTotalCarbs")
                .avg("totalSugar").as("avgTotalSugar")
                .avg("totalProtein").as("avgTotalProtein")
                .avg("totalFibre").as("avgTotalFibre"));

        AggregationResults<StatsDTO.Averages> results =
                mongoTemplate.aggregate(newAggregation(operations), COLLECTION_NAME, StatsDTO.Averages.class);

        return Optional.ofNullable(results.getUniqueMappedResult());
    }

    /**
     * Returns the top or bottom N days for one metric, optionally scoped to a date range.
     *
     * @param metric    the metric to rank days by
     * @param direction whether the highest or the lowest days are wanted
     * @param limit     the maximum number of days to return
     * @param fromDate  the earliest date to include, or null for no lower bound
     * @param toDate    the latest date to include, or null for no upper bound
     * @return the matching days with their value for the metric, most extreme first
     */
    public List<StatsDTO.DayStats> findExtremeDays(NutrientMetric metric, ExtremeDirection direction, int limit,
                                                   LocalDate fromDate, LocalDate toDate) {
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

        return results.getMappedResults();
    }

    /**
     * Finds all six nutrient extremes in one aggregation. BSON compares {@code {v: total, d: date}}
     * in field order, so {@code $max} selects the highest total, then the latest date on ties.
     *
     * @return the highest day per metric, with metrics missing from the map if the diary is empty
     */
    public Map<NutrientMetric, StatsDTO.DayStats> findHighestDayPerMetric() {
        Document group = new Document("_id", null);
        Document project = new Document("_id", 0);
        for (NutrientMetric metric : NutrientMetric.values()) {
            String alias = metric.getFieldName();
            group.append(alias, new Document("$max",
                    new Document("v", "$" + metric.getFieldName()).append("d", "$date")));
            project.append(alias, new Document("total", "$" + alias + ".v").append("date", "$" + alias + ".d"));
        }

        // Let MongoDB's converter map BSON dates to LocalDate.
        AggregationResults<HighestDays> results = mongoTemplate.aggregate(
                newAggregation(
                        context -> new Document("$group", group),
                        context -> new Document("$project", project)),
                COLLECTION_NAME, HighestDays.class);

        HighestDays highest = results.getUniqueMappedResult();
        if (highest == null) {
            return Map.of();
        }

        Map<NutrientMetric, StatsDTO.DayStats> byMetric = new EnumMap<>(NutrientMetric.class);
        for (NutrientMetric metric : NutrientMetric.values()) {
            StatsDTO.DayStats dayStats = highest.forMetric(metric);
            if (dayStats != null && dayStats.getDate() != null) {
                byMetric.put(metric, dayStats);
            }
        }
        return byMetric;
    }

    /**
     * Projected date and total for each nutrient extreme.
     */
    @Data
    static class HighestDays {

        private StatsDTO.DayStats totalCalories;
        private StatsDTO.DayStats totalFat;
        private StatsDTO.DayStats totalCarbs;
        private StatsDTO.DayStats totalSugar;
        private StatsDTO.DayStats totalProtein;
        private StatsDTO.DayStats totalFibre;

        StatsDTO.DayStats forMetric(NutrientMetric metric) {
            return switch (metric) {
                case CALORIES -> totalCalories;
                case FAT -> totalFat;
                case CARBS -> totalCarbs;
                case SUGAR -> totalSugar;
                case PROTEIN -> totalProtein;
                case FIBRE -> totalFibre;
            };
        }
    }

    /**
     * @return how many distinct product names appear anywhere in the diary
     */
    public long countUniqueProducts() {
        return countFromAggregation("uniqueCount", unwind("products"), group("products.name"), count().as("uniqueCount"));
    }

    /**
     * @return how many product entries there are in total, counting repeats
     */
    public long countTotalProducts() {
        return countFromAggregation("totalCount", unwind("products"), count().as("totalCount"));
    }

    private long countFromAggregation(String field, AggregationOperation... operations) {
        AggregationResults<Document> results =
                mongoTemplate.aggregate(newAggregation(operations), COLLECTION_NAME, Document.class);
        Document document = results.getUniqueMappedResult();
        if (document == null) {
            return 0L;
        }
        Object value = document.get(field);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * Loads daily totals oldest first, excluding products to reduce memory use.
     *
     * @param fromDate the first date to load, or null for no lower bound
     * @param toDate   the last date to load, or null for no upper bound
     * @return the matching entries ordered by date ascending
     */
    public List<FddbData> findDailyTotals(LocalDate fromDate, LocalDate toDate) {
        Query query = new Query();
        Criteria criteria = buildDateCriteria(fromDate, toDate);
        if (criteria != null) {
            query.addCriteria(criteria);
        }
        query.fields().exclude("products");
        query.with(Sort.by(Sort.Direction.ASC, "date"));

        return mongoTemplate.find(query, FddbData.class, COLLECTION_NAME);
    }

    /**
     * Loads dates with positive calories; empty exports count as unlogged.
     *
     * @param fromDate the first date to load
     * @param toDate   the last date to load
     * @return the logged dates, for cheap membership checks
     */
    public Set<LocalDate> findLoggedDates(LocalDate fromDate, LocalDate toDate) {
        Query query = new Query(Criteria.where("date").gte(fromDate).lte(toDate).and("totalCalories").gt(0));
        query.fields().exclude("products");

        Set<LocalDate> loggedDates = new HashSet<>();
        for (FddbData entry : mongoTemplate.find(query, FddbData.class, COLLECTION_NAME)) {
            loggedDates.add(entry.getDate());
        }
        return loggedDates;
    }
}
