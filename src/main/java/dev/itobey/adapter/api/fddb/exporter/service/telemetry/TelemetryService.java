package dev.itobey.adapter.api.fddb.exporter.service.telemetry;

import dev.itobey.adapter.api.fddb.exporter.adapter.TelemetryApi;
import dev.itobey.adapter.api.fddb.exporter.domain.ExecutionMode;
import dev.itobey.adapter.api.fddb.exporter.dto.telemetry.TelemetryDto;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.PersistenceService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${fddb-exporter.fddb.username}")
    private String fddbUserMail;
    @Value("${fddb-exporter.persistence.mongodb.enabled}")
    private boolean mongodbEnabled;
    @Value("${fddb-exporter.persistence.influxdb.enabled}")
    private boolean influxdbEnabled;

    public void sendTelemetryData() {
        ExecutionMode executionMode = environmentDetector.getExecutionMode();
        long documentCount = persistenceService.countAllEntries();
        String mailHash = hashMail(fddbUserMail);
        TelemetryDto telemetryDto = new TelemetryDto();
        telemetryDto.setMailHash(mailHash);
        telemetryDto.setDocumentCount(documentCount);
        telemetryDto.setMongodbEnabled(mongodbEnabled);
        telemetryDto.setInfluxdbEnabled(influxdbEnabled);
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
