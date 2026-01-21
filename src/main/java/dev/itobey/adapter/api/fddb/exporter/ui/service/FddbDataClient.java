package dev.itobey.adapter.api.fddb.exporter.ui.service;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Client service for FDDB data export and query API endpoints.
 */
@Service
@Slf4j
public class FddbDataClient {

    private static final String FDDBDATA_URL = "/api/v2/fddbdata";
    private static final String EXPORT_URL = "/api/v2/fddbdata/export?days={days}&includeToday={includeToday}";
    private static final String DATE_URL = "/api/v2/fddbdata/{date}";

    private final RestTemplate restTemplate;

    public FddbDataClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Export data for a date range.
     *
     * @param dateRange the date range to export
     * @return ExportResultDTO with results
     * @throws ApiException if the API call fails
     */
    public ExportResultDTO exportForDateRange(DateRangeDTO dateRange) throws ApiException {
        try {
            return restTemplate.postForObject(getBaseUrl() + FDDBDATA_URL, dateRange, ExportResultDTO.class);
        } catch (RestClientException e) {
            log.error("Failed to export for date range", e);
            throw new ApiException("Failed to export for date range: " + e.getMessage(), e);
        }
    }

    /**
     * Export data for recent days.
     *
     * @param days         number of days to export
     * @param includeToday whether to include today
     * @return ExportResultDTO with results
     * @throws ApiException if the API call fails
     */
    public ExportResultDTO exportForDaysBack(int days, boolean includeToday) throws ApiException {
        try {
            String url = getBaseUrl() + EXPORT_URL;
            return restTemplate.getForObject(url, ExportResultDTO.class, days, includeToday);
        } catch (RestClientException e) {
            log.error("Failed to export for days back", e);
            throw new ApiException("Failed to export for days back: " + e.getMessage(), e);
        }
    }

    /**
     * Get all FDDB data entries.
     *
     * @return List of FddbDataDTO
     * @throws ApiException if the API call fails
     */
    public List<FddbDataDTO> getAllEntries() throws ApiException {
        try {
            ResponseEntity<List<FddbDataDTO>> response = restTemplate.exchange(
                    getBaseUrl() + FDDBDATA_URL,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get all entries", e);
            throw new ApiException("Failed to retrieve entries: " + e.getMessage(), e);
        }
    }

    /**
     * Get FDDB data for a specific date.
     *
     * @param date the date in YYYY-MM-DD format
     * @return FddbDataDTO for the date
     * @throws ApiException if the API call fails
     */
    public FddbDataDTO getByDate(String date) throws ApiException {
        try {
            String url = getBaseUrl() + DATE_URL;
            return restTemplate.getForObject(url, FddbDataDTO.class, date);
        } catch (HttpClientErrorException.NotFound e) {
            log.error("No data found for date: {}", date);
            throw new ApiException("No data available for this day.");
        } catch (RestClientException e) {
            log.error("Failed to get data for date: {}", date, e);
            throw new ApiException("Failed to retrieve data for date: " + e.getMessage(), e);
        }
    }

    /**
     * Search products by name.
     *
     * @param name the product name to search
     * @return List of ProductWithDateDTO
     * @throws ApiException if the API call fails
     */
    public List<ProductWithDateDTO> searchProducts(String name) throws ApiException {
        return searchProducts(name, null);
    }

    /**
     * Search products by name, optionally filtered by days of the week.
     *
     * @param name the product name to search
     * @param days optional list of days of the week to filter results
     * @return List of ProductWithDateDTO
     * @throws ApiException if the API call fails
     */
    public List<ProductWithDateDTO> searchProducts(String name, List<DayOfWeek> days) throws ApiException {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromUriString(getBaseUrl() + "/api/v2/fddbdata/products")
                    .queryParam("name", name);

            if (days != null && !days.isEmpty()) {
                uriBuilder.queryParam("days", days.toArray());
            }

            ResponseEntity<List<ProductWithDateDTO>> response = restTemplate.exchange(
                    uriBuilder.build().encode().toUri(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to search products: {}", name, e);
            throw new ApiException("Failed to search products: " + e.getMessage(), e);
        }
    }

    private String getBaseUrl() {
        return "http://localhost:8080";
    }
}

