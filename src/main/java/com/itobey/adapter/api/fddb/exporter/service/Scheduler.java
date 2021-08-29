package com.itobey.adapter.api.fddb.exporter.service;

import com.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthenticationException;

import javax.inject.Singleton;

/**
 * Micronaut scheduler to periodically execute a job.
 */
@Singleton
@RequiredArgsConstructor
@Slf4j
public class Scheduler {

    private final TimeframeCalculator timeframeCalculator;
    private final ExportService exportService;

    /**
     * Runs the FDDB export for yesterday every day at 3 AM.
     */
    @Scheduled(cron = "0 3 * * *")
    public void runFddbExportForYesterday() {
        Timeframe timeframe = timeframeCalculator.calculateTimeframeForYesterday();
        try {
            exportService.exportDataAndSaveToDb(timeframe);
        } catch (AuthenticationException authenticationException) {
            log.error("not logged in - skipping job execution");
            // TODO alerting?
        } catch (ParseException parseException) {
            log.warn("data for yesterday cannot be parsed, skipping this day");
        }
    }
}
