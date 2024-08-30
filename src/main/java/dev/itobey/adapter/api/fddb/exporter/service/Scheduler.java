package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/**
 * Micronaut scheduler to periodically execute a job.
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class Scheduler {

    private final TimeframeCalculator timeframeCalculator;
    private final ExportService exportService;

    /**
     * Runs the FDDB export for yesterday every day at 3 AM.
     */
    @Scheduled(cron = "0 3 * * * *")
    public void runFddbExportForYesterday() {
        Timeframe timeframe = timeframeCalculator.calculateTimeframeForYesterday();
        try {
            exportService.exportData(timeframe);
        } catch (AuthenticationException authenticationException) {
            log.error("not logged in - skipping job execution");
            // TODO alerting?
        } catch (ParseException parseException) {
            log.warn("data for yesterday cannot be parsed, skipping this day");
        }
    }
}
