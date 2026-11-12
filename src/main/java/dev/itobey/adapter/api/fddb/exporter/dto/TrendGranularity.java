package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.util.Locale;

/**
 * Bucket size for time series returned by the trend endpoint.
 * <p>
 * {@code WEEK} buckets follow ISO-8601 weeks (Monday to Sunday).
 */
@Schema(description = "Bucket size for a trend time series")
public enum TrendGranularity {

    DAY,
    WEEK,
    MONTH;

    public String bucketLabel(LocalDate date) {
        return switch (this) {
            case DAY -> date.toString();
            case WEEK -> String.format(Locale.ROOT, "%d-W%02d",
                    date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTH -> YearMonth.from(date).toString();
        };
    }
}
