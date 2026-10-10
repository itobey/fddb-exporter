package dev.itobey.adapter.api.fddb.exporter.service.telemetry;

import dev.itobey.adapter.api.fddb.exporter.adapter.TelemetryApi;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.domain.ExecutionMode;
import dev.itobey.adapter.api.fddb.exporter.dto.telemetry.TelemetryDto;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.PersistenceService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * This service is used to send telemetry data. No personal data is sent.
 * Only the mail hash is sent along with the document count, which optional features are enabled (persistence layers,
 * MCP server) and the execution mode, to determine how the exporter is used.
 * See README.md for more information.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryService {

    private static final String UNKNOWN_VERSION = "dev";

    private final TelemetryApi telemetryApi;
    private final PersistenceService persistenceService;
    private final EnvironmentDetector environmentDetector;
    /**
     * Optional for the same reason as in {@link dev.itobey.adapter.api.fddb.exporter.service.VersionCheckService}:
     * running from an IDE without a Maven build produces no {@code BuildProperties} bean, and a missing
     * version string must not stop the application from starting.
     */
    private final Optional<BuildProperties> buildProperties;
    private final FddbExporterProperties properties;

    public void sendTelemetryData() {
        ExecutionMode executionMode = environmentDetector.getExecutionMode();
        String mailHash = hashMail(properties.getFddb().getUsername());
        TelemetryDto telemetryDto = new TelemetryDto();
        boolean mongoDbEnabled = properties.getPersistence().getMongodb().isEnabled();
        if (mongoDbEnabled) {
            long documentCount = persistenceService.countAllEntries();
            telemetryDto.setDocumentCount(documentCount);
        }
        boolean influxDbEnabled = properties.getPersistence().getInfluxdb().isEnabled();
        if (influxDbEnabled) {
            long pointCount = persistenceService.countAllInfluxDbPoints();
            telemetryDto.setPointCount(pointCount);
        }
        boolean mcpEnabled = properties.getMcp().isEnabled();
        telemetryDto.setMailHash(mailHash);
        telemetryDto.setMongodbEnabled(mongoDbEnabled);
        telemetryDto.setInfluxdbEnabled(influxDbEnabled);
        telemetryDto.setMcpEnabled(mcpEnabled);
        // the write-tools flag does nothing while the MCP server itself is off, so report the effective state
        telemetryDto.setMcpWriteToolsEnabled(mcpEnabled && properties.getMcp().isWriteToolsEnabled());
        telemetryDto.setExecutionMode(executionMode);
        telemetryDto.setAppVersion(buildProperties.map(BuildProperties::getVersion).orElse(UNKNOWN_VERSION));
        log.debug("sending telemetry data: {}", telemetryDto);
        telemetryApi.sendTelemetryData(telemetryDto);
    }

    /**
     * Sends the ping and swallows anything that goes wrong.
     * <p>
     * A telemetry outage - an unreachable host, a DNS failure, an air-gapped deployment, or the documented
     * opt-out of repointing {@code fddb-exporter.telemetry.url} at a dead address - must never be visible to
     * the user, and above all must never fail the {@code @PostConstruct} below and with it the whole startup.
     */
    public void sendTelemetryDataQuietly() {
        try {
            sendTelemetryData();
        } catch (Exception exception) {
            log.debug("could not send telemetry data: {}", exception.getMessage());
        }
    }

    @PostConstruct
    private void init() {
        log.debug("sending telemetry data on startup");
        sendTelemetryDataQuietly();
    }

    private String hashMail(String mail) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(mail.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            return "";
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

}
