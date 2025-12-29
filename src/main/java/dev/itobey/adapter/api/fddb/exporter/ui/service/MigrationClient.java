package dev.itobey.adapter.api.fddb.exporter.ui.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Client service for migration API endpoints.
 */
@Service
@Slf4j
public class MigrationClient {

    private static final String MIGRATION_URL = "/api/v2/migration/toInfluxDb";

    private final RestTemplate restTemplate;

    public MigrationClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Migrate all MongoDB entries to InfluxDB.
     *
     * @return migration result message
     * @throws ApiException if the API call fails
     */
    public String migrateToInfluxDb() throws ApiException {
        try {
            return restTemplate.postForObject(getBaseUrl() + MIGRATION_URL, null, String.class);
        } catch (RestClientException e) {
            log.error("Failed to migrate to InfluxDB", e);
            throw new ApiException("Failed to migrate to InfluxDB: " + e.getMessage(), e);
        }
    }

    private String getBaseUrl() {
        return "http://localhost:8080";
    }
}

