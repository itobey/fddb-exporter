package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

/**
 * How the value of a day is compared against a nutritional target.
 * <p>
 * Deliberately limited to the two directions a diet goal actually takes: a floor ("at least 120 g
 * of protein") or a ceiling ("at most 2500 kcal"). Equality is not offered - no day ever hits a
 * macro target exactly, so a tool that allowed it would only invite empty results.
 */
public enum GoalComparator {

    /**
     * The day passes when its value is greater than or equal to the target.
     */
    AT_LEAST,

    /**
     * The day passes when its value is less than or equal to the target.
     */
    AT_MOST
}
