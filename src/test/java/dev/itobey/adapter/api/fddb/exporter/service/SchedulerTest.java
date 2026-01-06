package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.service.telemetry.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

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

