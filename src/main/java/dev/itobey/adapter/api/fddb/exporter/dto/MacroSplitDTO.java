package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Share of energy coming from fat, carbs and protein over a date range.
 * <p>
 * The split is <b>kcal-weighted, not gram-weighted</b>: grams are converted with the Atwater
 * factors (fat 9 kcal/g, carbs and protein 4 kcal/g) before the percentages are computed.
 * Because of that, {@code macroCalories} is derived from the macros and will usually differ
 * slightly from {@code averageCalories}, which is the calorie figure FDDB itself reports.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "kcal-weighted share of energy from fat, carbs and protein")
public class MacroSplitDTO {

    @Schema(description = "Start date of the range", example = "2024-01-01")
    String fromDate;

    @Schema(description = "End date of the range", example = "2024-01-31")
    String toDate;

    @Schema(description = "Percentage of energy from fat", example = "34.5")
    double fatPercentage;

    @Schema(description = "Percentage of energy from carbs", example = "45.2")
    double carbsPercentage;

    @Schema(description = "Percentage of energy from protein", example = "20.3")
    double proteinPercentage;

    @Schema(description = "Average daily kcal from fat", example = "690.3")
    double fatCalories;

    @Schema(description = "Average daily kcal from carbs", example = "904.8")
    double carbsCalories;

    @Schema(description = "Average daily kcal from protein", example = "406.4")
    double proteinCalories;

    @Schema(description = "Average daily kcal derived from the macros (fat*9 + carbs*4 + protein*4)", example = "2001.5")
    double macroCalories;

    @Schema(description = "Average daily kcal as reported by FDDB", example = "2000.5")
    double averageCalories;
}
