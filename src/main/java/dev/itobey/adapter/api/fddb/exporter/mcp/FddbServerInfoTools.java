package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.ServerInfoDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import dev.itobey.adapter.api.fddb.exporter.service.VersionCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The introspection tool: what this server is, what it is configured with, and what day it is.
 * <p>
 * The last one is the reason it exists. Every relative date parameter resolves against the server's
 * today, and an agent's own idea of today is regularly stale - one cheap call at the start of a
 * conversation anchors everything that follows.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = {"fddb-exporter.mcp.enabled", "fddb-exporter.persistence.mongodb.enabled"},
        havingValue = "true")
public class FddbServerInfoTools {

    private final FddbDataService fddbDataService;
    private final VersionCheckService versionCheckService;
    private final FddbExporterProperties properties;

    @McpTool(
            name = "get_server_info",
            description = """
                    Returns what this server is running and what it holds: the application version, \
                    today's date as the server sees it, which stores are enabled, whether exports \
                    run on a schedule, and the first and last day the diary has an entry for. Call \
                    this at the start of a conversation - it is the cheapest way to learn today's \
                    date, which every relative date parameter is resolved against, and the window \
                    the data actually covers.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ServerInfoDTO getServerInfo() {
        log.debug("MCP: returning server info");

        StatsDTO stats = fddbDataService.getStats();
        FddbExporterProperties.Scheduler scheduler = properties.getScheduler();
        boolean schedulerEnabled = scheduler != null && scheduler.isEnabled();

        return ServerInfoDTO.builder()
                .appVersion(versionCheckService.getCurrentVersion())
                .serverDate(LocalDate.now())
                .timeZone(ZoneId.systemDefault().getId())
                .mongodbEnabled(properties.getPersistence().getMongodb().isEnabled())
                .influxdbEnabled(properties.getPersistence().getInfluxdb().isEnabled())
                .schedulerEnabled(schedulerEnabled)
                .schedulerCron(schedulerEnabled ? scheduler.getCron() : null)
                .writeToolsEnabled(properties.getMcp() != null && properties.getMcp().isWriteToolsEnabled())
                .firstEntryDate(stats.getFirstEntryDate())
                .lastEntryDate(stats.getLastEntryDate())
                .entryCount(stats.getAmountEntries())
                .build();
    }
}
