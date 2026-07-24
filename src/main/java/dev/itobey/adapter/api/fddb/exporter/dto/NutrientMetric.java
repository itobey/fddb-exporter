package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The nutritional metrics a day can be ranked or trended by.
 * <p>
 * Each constant knows the name of the corresponding daily-total field in the {@code fddb} collection,
 * so callers never have to pass raw field names into an aggregation.
 */
@Schema(description = "Nutritional metric of a day")
public enum NutrientMetric {

    CALORIES("totalCalories"),
    FAT("totalFat"),
    CARBS("totalCarbs"),
    SUGAR("totalSugar"),
    PROTEIN("totalProtein"),
    FIBRE("totalFibre");

    private final String fieldName;

    NutrientMetric(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
