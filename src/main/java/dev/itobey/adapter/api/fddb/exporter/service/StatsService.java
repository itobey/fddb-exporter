package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
public class StatsService {

    public static final String COLLECTION_NAME = "fddb";

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    public StatsDTO getStats() {
        long amountEntries = getAmountEntries();
        LocalDate firstEntryDate = getFirstEntryDate();
        double entryPercentage = calculateEntryPercentage(firstEntryDate, amountEntries);
        StatsDTO.Averages averageTotals = getAverageTotals();
        StatsDTO.Averages last7DaysAverage = getLast7DaysAverage();
        StatsDTO.Averages last30DaysAverage = getLast30DaysAverage();
        long uniqueProducts = getUniqueProductsCount();

        return StatsDTO.builder()
                .amountEntries(amountEntries)
                .firstEntryDate(firstEntryDate)
                .entryPercentage(entryPercentage)
                .uniqueProducts(uniqueProducts)
                .averageTotals(averageTotals)
                .last7DaysAverage(last7DaysAverage)
                .last30DaysAverage(last30DaysAverage)
                .highestCaloriesDay(getDayWithHighestTotal("totalCalories"))
                .highestFatDay(getDayWithHighestTotal("totalFat"))
                .highestCarbsDay(getDayWithHighestTotal("totalCarbs"))
                .highestProteinDay(getDayWithHighestTotal("totalProtein"))
                .highestFibreDay(getDayWithHighestTotal("totalFibre"))
                .highestSugarDay(getDayWithHighestTotal("totalSugar"))
                .build();
    }

    private long getAmountEntries() {
        return mongoTemplate.count(new Query(), COLLECTION_NAME);
    }

    private LocalDate getFirstEntryDate() {
        if (mongoTemplate == null) {
            throw new IllegalStateException("MongoDB is not configured");
        }
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "date")).limit(1);
        FddbData firstDocument = mongoTemplate.findOne(query, FddbData.class, COLLECTION_NAME);
        if (firstDocument == null) {
            throw new IllegalStateException("No entries found in database");
        }
        return firstDocument.getDate();
    }

    private StatsDTO.Averages getAverageTotals() {
        return getAverages(null);
    }

    private StatsDTO.Averages getLast7DaysAverage() {
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
        return getAverages(Criteria.where("date").gte(sevenDaysAgo));
    }

    private StatsDTO.Averages getLast30DaysAverage() {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        return getAverages(Criteria.where("date").gte(thirtyDaysAgo));
    }

    private StatsDTO.DayStats getDayWithHighestTotal(String totalField) {
        Aggregation aggregation = newAggregation(
                sort(Sort.Direction.DESC, totalField),
                limit(1),
                project("date").and(totalField).as("total")
        );
        return mongoTemplate.aggregate(aggregation, COLLECTION_NAME, StatsDTO.DayStats.class).getUniqueMappedResult();
    }

    private StatsDTO.Averages getAverages(Criteria criteria) {
        if (mongoTemplate == null) {
            throw new IllegalStateException("MongoDB is not configured");
        }

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

        // Round all averages to one decimal place
        averages.setAvgTotalCalories(round1(averages.getAvgTotalCalories()));
        averages.setAvgTotalFat(round1(averages.getAvgTotalFat()));
        averages.setAvgTotalCarbs(round1(averages.getAvgTotalCarbs()));
        averages.setAvgTotalSugar(round1(averages.getAvgTotalSugar()));
        averages.setAvgTotalProtein(round1(averages.getAvgTotalProtein()));
        averages.setAvgTotalFibre(round1(averages.getAvgTotalFibre()));

        return averages;
    }

    private double calculateEntryPercentage(LocalDate givenDate, long documentCount) {
        long daysSince = ChronoUnit.DAYS.between(givenDate, LocalDate.now());
        return (double) documentCount / daysSince * 100;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
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

}
