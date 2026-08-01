package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;

/**
 * Reads a single {@link NutrientMetric} off a day or a set of averages, and names its unit.
 * <p>
 * The unit is stated in every response that reports a bare metric value. The daily totals are a mix
 * of kcal and grams in one flat object, so a response that says only {@code "total": 180} leaves the
 * agent guessing - and a guess there turns into a confident sentence about someone's diet.
 */
final class McpMetrics {

    static final String KCAL = "kcal";

    static final String GRAMS = "g";

    private McpMetrics() {
    }

    /**
     * @param metric the metric in question
     * @return the unit its values are expressed in
     */
    static String unitOf(NutrientMetric metric) {
        return metric == NutrientMetric.CALORIES ? KCAL : GRAMS;
    }

    /**
     * @param entry  the day to read
     * @param metric the metric to read
     * @return the daily total of that metric
     */
    static double valueOf(FddbDataDTO entry, NutrientMetric metric) {
        return switch (metric) {
            case CALORIES -> entry.getTotalCalories();
            case FAT -> entry.getTotalFat();
            case CARBS -> entry.getTotalCarbs();
            case SUGAR -> entry.getTotalSugar();
            case PROTEIN -> entry.getTotalProtein();
            case FIBRE -> entry.getTotalFibre();
        };
    }

    /**
     * @param averages the averages to read
     * @param metric   the metric to read
     * @return the average of that metric
     */
    static double valueOf(StatsDTO.Averages averages, NutrientMetric metric) {
        return switch (metric) {
            case CALORIES -> averages.getAvgTotalCalories();
            case FAT -> averages.getAvgTotalFat();
            case CARBS -> averages.getAvgTotalCarbs();
            case SUGAR -> averages.getAvgTotalSugar();
            case PROTEIN -> averages.getAvgTotalProtein();
            case FIBRE -> averages.getAvgTotalFibre();
        };
    }

    /**
     * Rounds to one decimal, the precision every other aggregation in the app reports - nobody needs
     * {@code 2143.7000000000003}.
     *
     * @param value the value to round
     * @return the rounded value
     */
    static double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
