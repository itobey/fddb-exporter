package dev.itobey.adapter.api.fddb.exporter.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Value;

import java.time.LocalDate;

@Data
@Builder
public class StatsDTO {
    long documentCount;
    LocalDate earliestDate;
    double entryPercentage;
    Averages averageTotals;
    Averages last7DaysAverage;
    DayStats highestCaloriesDay;
    DayStats highestFatDay;
    DayStats highestCarbsDay;
    DayStats highestProteinDay;
    DayStats highestFibreDay;
    DayStats highestSugarDay;

    @Data
    @Builder
    public static class Averages {
        double avgTotalCalories;
        double avgTotalFat;
        double avgTotalCarbs;
        double avgTotalSugar;
        double avgTotalProtein;
        double avgTotalFibre;
    }

    @Value
    @Builder
    public static class DayStats {
        LocalDate date;
        double total;
    }
}