package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One nutritional target a day is checked against: a metric, a direction and a value.
 * <p>
 * This is a tool <em>input</em> type rather than a stored one - the app has no concept of a diet
 * goal, so the targets are whatever the user states in the conversation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalTargetDTO {

    @JsonPropertyDescription("The daily metric this target applies to")
    private NutrientMetric metric;

    @JsonPropertyDescription("AT_LEAST for a floor (protein, fibre), AT_MOST for a ceiling (calories, sugar)")
    private GoalComparator comparator;

    @JsonPropertyDescription("The target value: kcal for CALORIES, grams for every other metric")
    private double value;
}
