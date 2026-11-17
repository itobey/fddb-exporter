package dev.itobey.adapter.api.fddb.exporter.domain;

public enum BodyMetric {
    WEIGHT_KG(false),
    BODY_FAT_PERCENT(true),
    BODY_WATER_PERCENT(true),
    WAIST_CM(false),
    HIP_CM(false);

    private final boolean percentage;

    BodyMetric(boolean percentage) {
        this.percentage = percentage;
    }

    void validate(Double value) {
        if (value == null || !Double.isFinite(value)
                || (percentage ? value < 0 || value > 100 : value <= 0)) {
            throw new IllegalArgumentException("Invalid value for " + name());
        }
    }
}
