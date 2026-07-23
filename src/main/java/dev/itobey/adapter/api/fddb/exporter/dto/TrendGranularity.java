package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Bucket size for time series returned by the trend endpoint.
 * <p>
 * {@code WEEK} buckets follow ISO-8601 weeks (Monday to Sunday).
 */
@Schema(description = "Bucket size for a trend time series")
public enum TrendGranularity {

    DAY,
    WEEK,
    MONTH
}
