package dev.itobey.adapter.api.fddb.exporter.util;

/**
 * Shared rounding for statistics, persistence and MCP responses.
 */
public final class Rounding {

    private Rounding() {
    }

    /**
     * Rounds to one decimal.
     *
     * @param value the value to round
     * @return the rounded value
     */
    public static double toOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * The share {@code part} makes up of {@code whole}, in percent and rounded to one decimal.
     *
     * @param part  the part
     * @param whole the whole, which may be zero
     * @return the percentage, or {@code 0.0} if {@code whole} is zero
     */
    public static double percentageOf(double part, double whole) {
        return whole == 0 ? 0.0 : toOneDecimal(part / whole * 100);
    }
}
