package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Calculator for everything time and date related.
 */
@Service
public class TimeframeCalculator {

    private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");
    private static final int OFFSET_HOURS = 2;
    private static final int DAY_HOURS = 24;

    /**
     * Calculates the {@link Timeframe} containing the epoch seconds from and to of yesterday.
     *
     * @return a {@link Timeframe} object containing the values
     */
    public Timeframe calculateTimeframeForYesterday() {
        return calculateTimeframeFor(LocalDate.now(ZONE_BERLIN).minusDays(1));
    }

    /**
     * Calculates the {@link Timeframe} containing the epoch seconds from and to of the given date.
     *
     * @param date the date for which to calculate the timeframe
     * @return a {@link Timeframe} object containing the values
     */
    public Timeframe calculateTimeframeFor(LocalDate date) {
        ZonedDateTime startOfDay = date.atStartOfDay(ZONE_BERLIN).plusHours(OFFSET_HOURS);
        ZonedDateTime endOfDay = startOfDay.plusHours(DAY_HOURS);

        return new Timeframe(startOfDay.toEpochSecond(), endOfDay.toEpochSecond());
    }
}