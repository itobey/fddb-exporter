package dev.itobey.adapter.api.fddb.exporter.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Pattern;

public record BodyMeasurement(String source, String externalId, Instant measuredAt, Map<BodyMetric, Double> metrics) {
    private static final Pattern SOURCE = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Instant MIN_TIMESTAMP = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_TIMESTAMP = Instant.parse("+10000-01-01T00:00:00Z");

    public BodyMeasurement {
        if (source == null || !SOURCE.matcher(source).matches()) {
            throw new IllegalArgumentException("Source must be a lowercase slug of up to 64 characters");
        }
        if (externalId == null || externalId.isBlank() || externalId.length() > 200
                || externalId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("External ID must contain 1 to 200 characters without control characters");
        }
        validateTimestamp(measuredAt);
        measuredAt = measuredAt.truncatedTo(ChronoUnit.MILLIS);
        if (metrics == null || metrics.isEmpty() || metrics.keySet().stream().anyMatch(key -> key == null)) {
            throw new IllegalArgumentException("At least one supported metric is required");
        }
        metrics.forEach(BodyMetric::validate);
        metrics = Map.copyOf(metrics);
    }

    public static void validateTimestamp(Instant timestamp) {
        if (timestamp == null || timestamp.isBefore(MIN_TIMESTAMP) || !timestamp.isBefore(MAX_TIMESTAMP)) {
            throw new IllegalArgumentException("Timestamp must be in the UTC years 0001 to 9999");
        }
    }
}
