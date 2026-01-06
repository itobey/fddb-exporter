package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import dev.itobey.adapter.api.fddb.exporter.service.telemetry.TelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final FddbDataService fddbDataService;
    private final TelemetryService telemetryService;
    private final FddbExporterProperties properties;
    private final TelegramService telegramService;
    private final VersionCheckService versionCheckService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        if (properties.getScheduler().isEnabled()) {
            taskRegistrar.addCronTask(this::runFddbExportForYesterday, properties.getScheduler().getCron());
        }
        taskRegistrar.addCronTask(this::sendTelemetryData, properties.getTelemetry().getCron());
        taskRegistrar.addCronTask(this::checkForNewVersion, properties.getTelemetry().getCron());
    }

    private void sendTelemetryData() {
        log.debug("sending telemetry data");
        telemetryService.sendTelemetryData();
    }

    private void checkForNewVersion() {
        log.debug("checking for new version");
        versionCheckService.checkForNewVersion();
    }

    private void runFddbExportForYesterday() {
        log.trace("starting scheduled export");
        try {
            fddbDataService.exportForDaysBack(1, false);
        } catch (AuthenticationException authenticationException) {
            log.error("not logged in - skipping job execution");
        } catch (ParseException parseException) {
            String errorMessage = "data for yesterday cannot be parsed, skipping this day";
            log.warn(errorMessage, parseException);
            if (properties.getNotification().isEnabled()) {
                telegramService.sendMessage(errorMessage);
            }
        }
    }
}