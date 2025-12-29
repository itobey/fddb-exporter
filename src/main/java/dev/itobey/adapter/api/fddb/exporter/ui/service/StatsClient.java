package dev.itobey.adapter.api.fddb.exporter.ui.service;

import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Client service for statistics-related API endpoints.
 */
@Service
@Slf4j
public class StatsClient {

    private static final String STATS_URL = "/api/v2/stats";
    private static final String AVERAGES_URL = "/api/v2/stats/averages?fromDate={fromDate}&toDate={toDate}";

    private final RestTemplate restTemplate;

    public StatsClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Get overall statistics.
     *
     * @return StatsDTO with overall statistics
     * @throws ApiException if the API call fails
     */
    public StatsDTO getStats() throws ApiException {
        try {
            return restTemplate.getForObject(getBaseUrl() + STATS_URL, StatsDTO.class);
        } catch (RestClientException e) {
            log.error("Failed to get stats", e);
            throw new ApiException("Failed to retrieve statistics: " + e.getMessage(), e);
        }
    }

    /**
     * Get rolling averages for a date range.
     *
     * @param fromDate start date in YYYY-MM-DD format
     * @param toDate   end date in YYYY-MM-DD format
     * @return RollingAveragesDTO with rolling averages
     * @throws ApiException if the API call fails
     */
    public dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO getRollingAverages(String fromDate, String toDate) throws ApiException {
        try {
            String url = getBaseUrl() + AVERAGES_URL;
            return restTemplate.getForObject(url, dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO.class, fromDate, toDate);
        } catch (RestClientException e) {
            log.error("Failed to get rolling averages", e);
            throw new ApiException("Failed to retrieve rolling averages: " + e.getMessage(), e);
        }
    }

    private String getBaseUrl() {
        return "http://localhost:8080";
    }
}

