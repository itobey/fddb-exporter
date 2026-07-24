package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Statistics about FDDB data entries")
public class StatsDTO {
    @Schema(description = "Total number of entries in the database", example = "365")
    long amountEntries;

    @Schema(description = "Date of the first entry", example = "2024-01-01")
    LocalDate firstEntryDate;

    @Schema(description = "Date of the most recent entry", example = "2024-12-22")
    LocalDate lastEntryDate;

    @Schema(description = "Percentage of days with entries", example = "95.5")
    double entryPercentage;

    @Schema(description = "Number of unique products consumed", example = "150")
    long uniqueProducts;

    @Schema(description = "Total number of products across all days", example = "2450")
    long totalProducts;

    @Schema(description = "Average nutritional values across all entries")
    Averages averageTotals;

    @Schema(description = "Day with highest calories")
    DayStats highestCaloriesDay;

    @Schema(description = "Day with highest fat intake")
    DayStats highestFatDay;

    @Schema(description = "Day with highest carbs intake")
    DayStats highestCarbsDay;

    @Schema(description = "Day with highest protein intake")
    DayStats highestProteinDay;

    @Schema(description = "Day with highest fibre intake")
    DayStats highestFibreDay;

    @Schema(description = "Day with highest sugar intake")
    DayStats highestSugarDay;

    @Schema(description = "Most recent day with no entry (excluding today). Returns date or 'only available with MongoDB' when MongoDB is not configured", example = "2024-12-20")
    Object mostRecentMissingDay;

    @Schema(description = "Number of days between the first entry and yesterday that have no entry. Null when MongoDB is not configured", example = "18")
    Long missingDaysCount;

    @Schema(description = "Days logged in a row up to now. Today is only counted once it has an entry, so a day still in progress does not break the streak. Null when MongoDB is not configured", example = "12")
    Integer currentStreak;

    @Schema(description = "Longest run of consecutive logged days since the first entry. Null when MongoDB is not configured", example = "94")
    Integer longestStreak;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Average nutritional values")
    public static class Averages {
        @Schema(description = "Average calories per day", example = "2000.5")
        double avgTotalCalories;

        @Schema(description = "Average fat per day in grams", example = "65.3")
        double avgTotalFat;

        @Schema(description = "Average carbs per day in grams", example = "250.2")
        double avgTotalCarbs;

        @Schema(description = "Average sugar per day in grams", example = "50.1")
        double avgTotalSugar;

        @Schema(description = "Average protein per day in grams", example = "80.4")
        double avgTotalProtein;

        @Schema(description = "Average fibre per day in grams", example = "25.6")
        double avgTotalFibre;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Statistics for a specific day")
    public static class DayStats {
        @Schema(description = "Date of the entry", example = "2024-12-22")
        LocalDate date;

        @Schema(description = "Total value for that day", example = "2500.5")
        double total;
    }
}
