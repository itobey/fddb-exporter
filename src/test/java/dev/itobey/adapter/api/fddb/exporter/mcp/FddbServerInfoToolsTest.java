package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.ServerInfoDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import dev.itobey.adapter.api.fddb.exporter.service.VersionCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FddbServerInfoToolsTest {

    @Mock
    private FddbDataService fddbDataService;
    @Mock
    private VersionCheckService versionCheckService;

    private FddbExporterProperties properties;
    private FddbServerInfoTools fddbServerInfoTools;

    @BeforeEach
    void setUp() {
        properties = propertiesWith(true, false, true, "0 0 3 * * *", false);
        fddbServerInfoTools = new FddbServerInfoTools(fddbDataService, versionCheckService, properties);
    }

    @Test
    void getServerInfo_shouldReportTodayAsTheServerSeesIt() {
        // given
        stubStats();
        when(versionCheckService.getCurrentVersion()).thenReturn("1.2.3");

        // when
        ServerInfoDTO info = fddbServerInfoTools.getServerInfo();

        // then the whole point of the tool: the agent does not have to guess the date
        assertEquals(LocalDate.now(), info.getServerDate());
        assertEquals(ZoneId.systemDefault().getId(), info.getTimeZone());
        assertEquals("1.2.3", info.getAppVersion());
    }

    @Test
    void getServerInfo_shouldReportTheConfigurationAndTheCoverageWindow() {
        // given
        stubStats();
        when(versionCheckService.getCurrentVersion()).thenReturn("dev");

        // when
        ServerInfoDTO info = fddbServerInfoTools.getServerInfo();

        // then
        assertTrue(info.isMongodbEnabled());
        assertFalse(info.isInfluxdbEnabled());
        assertTrue(info.isSchedulerEnabled());
        assertEquals("0 0 3 * * *", info.getSchedulerCron());
        assertFalse(info.isWriteToolsEnabled());
        assertEquals(LocalDate.of(2024, 1, 1), info.getFirstEntryDate());
        assertEquals(LocalDate.of(2024, 12, 22), info.getLastEntryDate());
        assertEquals(357, info.getEntryCount());
    }

    @Test
    void getServerInfo_shouldOmitTheCronWhenNothingIsScheduled() {
        // given
        fddbServerInfoTools = new FddbServerInfoTools(fddbDataService, versionCheckService,
                propertiesWith(true, true, false, "0 0 3 * * *", true));
        stubStats();
        when(versionCheckService.getCurrentVersion()).thenReturn("dev");

        // when
        ServerInfoDTO info = fddbServerInfoTools.getServerInfo();

        // then a cron next to schedulerEnabled=false reads like it still runs
        assertFalse(info.isSchedulerEnabled());
        assertNull(info.getSchedulerCron());
        assertTrue(info.isInfluxdbEnabled());
        assertTrue(info.isWriteToolsEnabled());
    }

    private void stubStats() {
        when(fddbDataService.getStats()).thenReturn(StatsDTO.builder()
                .amountEntries(357)
                .firstEntryDate(LocalDate.of(2024, 1, 1))
                .lastEntryDate(LocalDate.of(2024, 12, 22))
                .build());
    }

    private FddbExporterProperties propertiesWith(boolean mongodb, boolean influxdb, boolean scheduler,
                                                  String cron, boolean writeTools) {
        FddbExporterProperties.Persistence.MongoDB mongoDb = new FddbExporterProperties.Persistence.MongoDB();
        mongoDb.setEnabled(mongodb);
        FddbExporterProperties.Persistence.Influxdb influxDb = new FddbExporterProperties.Persistence.Influxdb();
        influxDb.setEnabled(influxdb);
        FddbExporterProperties.Persistence persistence = new FddbExporterProperties.Persistence();
        persistence.setMongodb(mongoDb);
        persistence.setInfluxdb(influxDb);

        FddbExporterProperties.Scheduler schedulerProperties = new FddbExporterProperties.Scheduler();
        schedulerProperties.setEnabled(scheduler);
        schedulerProperties.setCron(cron);

        FddbExporterProperties.Mcp mcp = new FddbExporterProperties.Mcp();
        mcp.setEnabled(true);
        mcp.setWriteToolsEnabled(writeTools);

        FddbExporterProperties fddbExporterProperties = new FddbExporterProperties();
        fddbExporterProperties.setPersistence(persistence);
        fddbExporterProperties.setScheduler(schedulerProperties);
        fddbExporterProperties.setMcp(mcp);
        return fddbExporterProperties;
    }
}
