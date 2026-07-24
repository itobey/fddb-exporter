package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of the "top products" ranking: a product name with the totals it contributed
 * across every day it was logged on.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A product with its aggregated contribution across all logged occurrences")
public class TopProductDTO {

    @Schema(description = "Product name as logged in FDDB", example = "Haferflocken kernig")
    private String name;

    @Schema(description = "How often the product was logged", example = "42")
    private long timesEaten;

    @Schema(description = "Sum of calories contributed by this product", example = "12600.5")
    private double totalCalories;

    @Schema(description = "Sum of fat in grams contributed by this product", example = "310.2")
    private double totalFat;

    @Schema(description = "Sum of carbs in grams contributed by this product", example = "1850.7")
    private double totalCarbs;

    @Schema(description = "Sum of protein in grams contributed by this product", example = "540.3")
    private double totalProtein;

    @Schema(description = "Average calories per logged occurrence", example = "300.0")
    private double averageCalories;
}
