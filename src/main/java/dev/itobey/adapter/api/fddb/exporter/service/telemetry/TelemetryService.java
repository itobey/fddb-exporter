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

/**
 * This service is used to send telemetry data. No personal data is sent.
 * Only the mail hash is sent along with the document count and the execution mode to determine how the exporter is used.
 * See README.md for more information.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryService {

    private final TelemetryApi telemetryApi;
    private final PersistenceService persistenceService;
    private final EnvironmentDetector environmentDetector;
    private final BuildProperties buildProperties;
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
        telemetryDto.setMailHash(mailHash);
        telemetryDto.setMongodbEnabled(mongoDbEnabled);
        telemetryDto.setInfluxdbEnabled(influxDbEnabled);
        telemetryDto.setExecutionMode(executionMode);
        telemetryDto.setAppVersion(buildProperties.getVersion());
        log.debug("sending telemetry data: {}", telemetryDto);
        telemetryApi.sendTelemetryData(telemetryDto);
    }

    @PostConstruct
    private void init() {
        log.debug("sending telemetry data on startup");
        sendTelemetryData();
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
