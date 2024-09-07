package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class StatsService {

    public static final String COLLECTION_NAME = "fddb";

    private final MongoTemplate mongoTemplate;

    public StatsDTO getStats() {
        long documentCount = getDocumentCount();
        LocalDate earliestDate = getEarliestDate();
        double entryPercentage = calculateEntryPercentage(earliestDate, documentCount);
        StatsDTO.Averages averageTotals = getAverageTotals();
        StatsDTO.Averages last7DaysAverage = getLast7DaysAverage();

        return StatsDTO.builder()
                .documentCount(documentCount)
                .earliestDate(earliestDate)
                .entryPercentage(entryPercentage)
                .averageTotals(averageTotals)
                .last7DaysAverage(last7DaysAverage)
                .highestCaloriesDay(getDayWithHighestTotal("totalCalories"))
                .highestFatDay(getDayWithHighestTotal("totalFat"))
                .highestCarbsDay(getDayWithHighestTotal("totalCarbs"))
                .highestProteinDay(getDayWithHighestTotal("totalProtein"))
                .highestFibreDay(getDayWithHighestTotal("totalFibre"))
                .highestSugarDay(getDayWithHighestTotal("totalSugar"))
                .build();
    }

    private long getDocumentCount() {
        return mongoTemplate.count(new Query(), COLLECTION_NAME);
    }

    private LocalDate getEarliestDate() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "date")).limit(1);
        FddbData firstDocument = mongoTemplate.findOne(query, FddbData.class, COLLECTION_NAME);
        return firstDocument != null ? firstDocument.getDate() : null;
    }

    private StatsDTO.Averages getAverageTotals() {
        return getAverages(null);
    }

    private StatsDTO.Averages getLast7DaysAverage() {
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
        return getAverages(Criteria.where("date").gte(sevenDaysAgo));
    }

    private StatsDTO.Averages getAverages(Criteria criteria) {
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
        return results.getUniqueMappedResult();
    }

    private StatsDTO.DayStats getDayWithHighestTotal(String totalField) {
        Aggregation aggregation = newAggregation(
                sort(Sort.Direction.DESC, totalField),
                limit(1),
                project("date").and(totalField).as("total")
        );
        return mongoTemplate.aggregate(aggregation, COLLECTION_NAME, StatsDTO.DayStats.class).getUniqueMappedResult();
    }

    private double calculateEntryPercentage(LocalDate givenDate, long documentCount) {
        long daysSince = ChronoUnit.DAYS.between(givenDate, LocalDate.now());
        return (double) documentCount / daysSince * 100;
    }
}