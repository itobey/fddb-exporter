package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Criterion used to rank products in the "top products" aggregation.
 * <p>
 * {@code FREQUENCY} ranks by how often a product was logged, every other constant ranks by the
 * summed contribution of that nutrient across all logged occurrences.
 */
@Schema(description = "Ranking criterion for the top products aggregation")
public enum ProductRanking {

    FREQUENCY("timesEaten"),
    CALORIES("totalCalories"),
    FAT("totalFat"),
    CARBS("totalCarbs"),
    PROTEIN("totalProtein");

    private final String fieldName;

    ProductRanking(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
