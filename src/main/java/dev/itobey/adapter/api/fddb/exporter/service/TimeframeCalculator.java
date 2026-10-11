package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.TimeframeDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Calculator for everything time and date related.
 */
@Service
@RequiredArgsConstructor
public class TimeframeCalculator {

    /**
     * FDDB diary days start two hours after local midnight.
     */
    private static final int DAY_START_HOUR = 2;

    private final FddbExporterProperties properties;

    /**
     * Calculates the {@link TimeframeDTO} containing the epoch seconds from and to of yesterday.
     *
     * @return a {@link TimeframeDTO} object containing the values
     */
    public TimeframeDTO calculateTimeframeForYesterday() {
        return calculateTimeframeFor(LocalDate.now(properties.resolveZone()).minusDays(1));
    }

    /**
     * Calculates the {@link TimeframeDTO} containing the epoch seconds from and to of the given date.
     * <p>
     * Resolves each day's boundary separately to avoid gaps or overlaps across DST changes.
     *
     * @param date the date for which to calculate the timeframe
     * @return a {@link TimeframeDTO} object containing the values
     */
    public TimeframeDTO calculateTimeframeFor(LocalDate date) {
        ZoneId zone = properties.resolveZone();
        ZonedDateTime startOfDay = date.atStartOfDay(zone).plusHours(DAY_START_HOUR);
        ZonedDateTime endOfDay = date.plusDays(1).atStartOfDay(zone).plusHours(DAY_START_HOUR);

        return new TimeframeDTO(startOfDay.toEpochSecond(), endOfDay.toEpochSecond() - 1);
    }
}
