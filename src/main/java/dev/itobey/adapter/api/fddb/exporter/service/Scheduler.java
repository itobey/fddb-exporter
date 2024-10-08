package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import dev.itobey.adapter.api.fddb.exporter.service.telemetry.TelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * This class is used to schedule the export of FDDb data and telemetry data.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class Scheduler implements SchedulingConfigurer {

    @Value("${fddb-exporter.scheduler.enabled}")
    private boolean schedulerEnabled;

    @Value("${fddb-exporter.scheduler.cron}")
    private String schedulerCron;

    private final FddbDataService fddbDataService;
    private final TelemetryService telemetryService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        if (schedulerEnabled) {
            taskRegistrar.addCronTask(this::runFddbExportForYesterday, schedulerCron);
        }
        taskRegistrar.addCronTask(this::sendTelemetryData, schedulerCron);
    }

    private void sendTelemetryData() {
        log.debug("sending telemetry data");
        telemetryService.sendTelemetryData();
    }

    private void runFddbExportForYesterday() {
        log.trace("starting scheduled export");
        try {
            fddbDataService.exportForDaysBack(1, false);
        } catch (AuthenticationException authenticationException) {
            log.error("not logged in - skipping job execution");
        } catch (ParseException parseException) {
            log.warn("data for yesterday cannot be parsed, skipping this day");
        }
    }
}