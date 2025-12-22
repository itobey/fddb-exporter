package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
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
        double entryPercentage = roundToOneDecimal(calculateEntryPercentage(firstEntryDate, amountEntries));
        StatsDTO.Averages averageTotals = roundAverages(getAverageTotals());

        return StatsDTO.builder()
                .amountEntries(amountEntries)
                .firstEntryDate(firstEntryDate)
                .entryPercentage(entryPercentage)
                .averageTotals(averageTotals)
                .highestCaloriesDay(roundDayStats(getDayWithHighestTotal("totalCalories")))
                .highestFatDay(roundDayStats(getDayWithHighestTotal("totalFat")))
                .highestCarbsDay(roundDayStats(getDayWithHighestTotal("totalCarbs")))
                .highestProteinDay(roundDayStats(getDayWithHighestTotal("totalProtein")))
                .highestFibreDay(roundDayStats(getDayWithHighestTotal("totalFibre")))
                .highestSugarDay(roundDayStats(getDayWithHighestTotal("totalSugar")))
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

    public StatsDTO.Averages getAveragesForDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("The 'from' date cannot be after the 'to' date");
        }

        Criteria criteria = Criteria.where("date").gte(fromDate).lte(toDate);
        return roundAverages(getAverages(criteria));
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

        return averages;
    }

    private double calculateEntryPercentage(LocalDate givenDate, long documentCount) {
        long daysSince = ChronoUnit.DAYS.between(givenDate, LocalDate.now());
        return (double) documentCount / daysSince * 100;
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
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
}
