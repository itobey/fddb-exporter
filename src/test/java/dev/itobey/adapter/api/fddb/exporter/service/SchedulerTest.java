package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ExportInProgressException;
import dev.itobey.adapter.api.fddb.exporter.service.telemetry.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerTest {

    @InjectMocks
    private Scheduler scheduler;

    @Mock
    private FddbDataService fddbDataService;
    @Mock
    private TelemetryService telemetryService;
    @Mock
    private FddbExporterProperties properties;
    @Mock
    private TelegramService telegramService;
    @Mock
    private VersionCheckService versionCheckService;
    @Mock
    private ScheduledTaskRegistrar taskRegistrar;

    private FddbExporterProperties.Scheduler schedulerProperties;
    private FddbExporterProperties.Telemetry telemetryProperties;
    private FddbExporterProperties.Notification notificationProperties;

    @BeforeEach
    void setUp() {
        schedulerProperties = new FddbExporterProperties.Scheduler();
        telemetryProperties = new FddbExporterProperties.Telemetry();
        notificationProperties = new FddbExporterProperties.Notification();

        lenient().when(properties.getScheduler()).thenReturn(schedulerProperties);
        lenient().when(properties.getTelemetry()).thenReturn(telemetryProperties);
        lenient().when(properties.getNotification()).thenReturn(notificationProperties);
    }

    @Test
    void configureTasks_shouldRegisterFddbExportTaskWhenEnabled() {
        // given
        schedulerProperties.setEnabled(true);
        schedulerProperties.setCron("0 0 1 * * ?");
        telemetryProperties.setCron("0 0 2 * * ?");

        // when
        scheduler.configureTasks(taskRegistrar);

        // then
        verify(taskRegistrar).addCronTask(any(Runnable.class), eq("0 0 1 * * ?"));
        verify(taskRegistrar, times(2)).addCronTask(any(Runnable.class), eq("0 0 2 * * ?"));
    }

    @Test
    void configureTasks_shouldNotRegisterFddbExportTaskWhenDisabled() {
        // given
        schedulerProperties.setEnabled(false);
        schedulerProperties.setCron("0 0 1 * * ?");
        telemetryProperties.setCron("0 0 2 * * ?");

        // when
        scheduler.configureTasks(taskRegistrar);

        // then
        verify(taskRegistrar, never()).addCronTask(any(Runnable.class), eq("0 0 1 * * ?"));
        verify(taskRegistrar, times(2)).addCronTask(any(Runnable.class), eq("0 0 2 * * ?"));
    }

    @Test
    void scheduledExport_shouldSkipQuietlyWhileAnotherExportIsRunning() {
        // given: a manual or MCP-triggered export holds the lock when the cron fires
        schedulerProperties.setEnabled(true);
        schedulerProperties.setCron("0 0 1 * * ?");
        telemetryProperties.setCron("0 0 2 * * ?");
        notificationProperties.setEnabled(true);
        doThrow(new ExportInProgressException("An export is already running"))
                .when(fddbDataService).exportForDaysBack(1, false);

        scheduler.configureTasks(taskRegistrar);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskRegistrar).addCronTask(task.capture(), eq("0 0 1 * * ?"));

        // when / then: the run is skipped, and it is not worth waking the user for
        assertDoesNotThrow(() -> task.getValue().run());
        verifyNoInteractions(telegramService);
    }

    @Test
    void scheduledExport_shouldNotifyAboutDaysThatCouldNotBeParsed() {
        // given: exportForTimerange collects a ParseException as an unsuccessful day rather than throwing,
        // so the result is the only place the failure shows up
        notificationProperties.setEnabled(true);
        when(fddbDataService.exportForDaysBack(1, false))
                .thenReturn(new ExportResultDTO(List.of(), List.of("2024-09-06")));

        // when
        captureScheduledExport().run();

        // then
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(telegramService).sendMessage(message.capture());
        assertTrue(message.getValue().contains("2024-09-06"));
    }

    @Test
    void scheduledExport_shouldNotNotify_whenNotificationsAreDisabled() {
        // given
        notificationProperties.setEnabled(false);
        when(fddbDataService.exportForDaysBack(1, false))
                .thenReturn(new ExportResultDTO(List.of(), List.of("2024-09-06")));

        // when
        captureScheduledExport().run();

        // then
        verifyNoInteractions(telegramService);
    }

    @Test
    void scheduledExport_shouldNotNotify_whenEveryDayWasExported() {
        // given
        when(fddbDataService.exportForDaysBack(1, false))
                .thenReturn(new ExportResultDTO(List.of("2024-09-06"), List.of()));

        // when
        captureScheduledExport().run();

        // then
        verifyNoInteractions(telegramService);
    }

    @Test
    void scheduledExport_shouldSwallowAnAuthenticationFailure() {
        // given: wrong credentials halt the run, but must not propagate out of the scheduled task
        doThrow(new AuthenticationException("not logged in"))
                .when(fddbDataService).exportForDaysBack(1, false);

        // when / then
        assertDoesNotThrow(() -> captureScheduledExport().run());
        verifyNoInteractions(telegramService);
    }

    /**
     * Registers the tasks and hands back the one bound to the export cron, so the failure paths of the
     * scheduled run can be driven directly.
     */
    private Runnable captureScheduledExport() {
        schedulerProperties.setEnabled(true);
        schedulerProperties.setCron("0 0 1 * * ?");
        telemetryProperties.setCron("0 0 2 * * ?");

        scheduler.configureTasks(taskRegistrar);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskRegistrar).addCronTask(task.capture(), eq("0 0 1 * * ?"));
        return task.getValue();
    }

    @Test
    void configureTasks_shouldAlwaysRegisterTelemetryTask() {
        // given
        schedulerProperties.setEnabled(false);
        telemetryProperties.setCron("0 0 2 * * ?");

        // when
        scheduler.configureTasks(taskRegistrar);

        // then
        // telemetry cron is used for both telemetry and version check; ensure at least one registration
        verify(taskRegistrar, atLeastOnce()).addCronTask(any(Runnable.class), eq("0 0 2 * * ?"));
    }

}

