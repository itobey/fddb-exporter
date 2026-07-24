package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregated view of every product matching a search term: how often it was eaten,
 * when it was first and last logged, what it contributed in total, and how the
 * occurrences distribute over the days of the week.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated summary for all products matching a search term")
public class ProductSummaryDTO {

    @Schema(description = "The search term this summary was built for", example = "haferflocken")
    private String searchTerm;

    @Schema(description = "Distinct product names that matched the search term")
    private List<String> matchedProductNames;

    @Schema(description = "How often a matching product was logged", example = "42")
    private long timesEaten;

    @Schema(description = "First date a matching product was logged", example = "2024-01-03")
    private LocalDate firstDate;

    @Schema(description = "Most recent date a matching product was logged", example = "2024-12-19")
    private LocalDate lastDate;

    @Schema(description = "Sum of calories contributed by matching products", example = "12600.5")
    private double totalCalories;

    @Schema(description = "Sum of fat in grams contributed by matching products", example = "310.2")
    private double totalFat;

    @Schema(description = "Sum of carbs in grams contributed by matching products", example = "1850.7")
    private double totalCarbs;

    @Schema(description = "Sum of protein in grams contributed by matching products", example = "540.3")
    private double totalProtein;

    @Schema(description = "Average calories per logged occurrence", example = "300.0")
    private double averageCalories;

    @Schema(description = "How the occurrences distribute over the days of the week")
    private Map<DayOfWeek, Long> weekdayDistribution;
}
