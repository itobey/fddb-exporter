package com.itobey.adapter.api.fddb.exporter.service;

import com.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import org.springframework.stereotype.Service;

import java.time.*;

/**
 * Calculator for everything time and date related.
 */
@Service
public class TimeframeCalculator {

    /**
     * Calculates the @{@link Timeframe} containing the epoch seconds from and to of yesterday.
     *
     * @return a {@link Timeframe} object containing the values
     */
    public Timeframe calculateTimeframeForYesterday() {
        ZoneId z = ZoneId.of("Europe/Berlin");
        ZonedDateTime startOfYesterday = ZonedDateTime.now(z).minusDays(1).toLocalDate().atStartOfDay(z).plusHours(2);
        ZonedDateTime endOfYesterday = startOfYesterday.plusHours(24);
        return Timeframe.builder()
                .from(startOfYesterday.toEpochSecond())
                .to(endOfYesterday.toEpochSecond())
                .build();
    }

    /**
     * Calculates the @{@link Timeframe} containing the epoch seconds from and to of yesterday.
     *
     * @return a {@link Timeframe} object containing the values
     */
    public Timeframe calculateTimeframeFor(LocalDate date) {
        ZoneId z = ZoneId.of("Europe/Berlin");
        ZonedDateTime startOfYesterday = date.atStartOfDay(z).plusHours(2);
        ZonedDateTime endOfYesterday = startOfYesterday.plusHours(24);
        return Timeframe.builder()
                .from(startOfYesterday.toEpochSecond())
                .to(endOfYesterday.toEpochSecond())
                .build();
    }

}
