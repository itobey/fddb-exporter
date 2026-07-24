package dev.itobey.adapter.api.fddb.exporter.ui.service;

import dev.itobey.adapter.api.fddb.exporter.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
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
    private static final String RANGE_URL = "/api/v2/fddbdata/range";
    private static final String PRODUCTS_URL = "/api/v2/fddbdata/products";
    private static final String TOP_PRODUCTS_URL = "/api/v2/fddbdata/products/top";
    private static final String PRODUCT_SUMMARY_URL = "/api/v2/fddbdata/products/summary";
    private static final String DISTINCT_PRODUCTS_URL = "/api/v2/fddbdata/products/distinct";

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
     * Get FDDB data entries for a date range.
     *
     * @param fromDate        start date in YYYY-MM-DD format
     * @param toDate          end date in YYYY-MM-DD format
     * @param includeProducts whether the product lists should be part of the response
     * @return List of FddbDataDTO, oldest first
     * @throws ApiException if the API call fails
     */
    public List<FddbDataDTO> getByDateRange(String fromDate, String toDate, boolean includeProducts) throws ApiException {
        try {
            URI uri = UriComponentsBuilder.fromUriString(getBaseUrl() + RANGE_URL)
                    .queryParam("fromDate", fromDate)
                    .queryParam("toDate", toDate)
                    .queryParam("includeProducts", includeProducts)
                    .build().encode().toUri();

            ResponseEntity<List<FddbDataDTO>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                    });
            return response.getBody();
        } catch (HttpClientErrorException.BadRequest e) {
            log.error("Invalid date range {} to {}", fromDate, toDate, e);
            throw new ApiException("Invalid date range: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            log.error("Failed to get entries for range {} to {}", fromDate, toDate, e);
            throw new ApiException("Failed to retrieve entries for the date range: " + e.getMessage(), e);
        }
    }

    /**
     * Get the top products ranked by frequency or by a nutrient total.
     *
     * @param ranking  the ranking criterion (e.g. FREQUENCY, CALORIES)
     * @param fromDate optional start date in YYYY-MM-DD format
     * @param toDate   optional end date in YYYY-MM-DD format
     * @param limit    the maximum number of products to return
     * @return List of TopProductDTO, highest first
     * @throws ApiException if the API call fails
     */
    public List<TopProductDTO> getTopProducts(ProductRanking ranking, String fromDate, String toDate, int limit)
            throws ApiException {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(getBaseUrl() + TOP_PRODUCTS_URL)
                    .queryParam("by", ranking)
                    .queryParam("limit", limit);
            if (fromDate != null) {
                uriBuilder.queryParam("fromDate", fromDate);
            }
            if (toDate != null) {
                uriBuilder.queryParam("toDate", toDate);
            }

            ResponseEntity<List<TopProductDTO>> response = restTemplate.exchange(
                    uriBuilder.build().encode().toUri(), HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                    });
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get top products", e);
            throw new ApiException("Failed to retrieve top products: " + e.getMessage(), e);
        }
    }

    /**
     * Get the aggregated summary of all products matching a search term.
     *
     * @param name     the product name to search
     * @param fromDate optional start date in YYYY-MM-DD format
     * @param toDate   optional end date in YYYY-MM-DD format
     * @return the ProductSummaryDTO
     * @throws ApiException if the API call fails
     */
    public ProductSummaryDTO getProductSummary(String name, String fromDate, String toDate) throws ApiException {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(getBaseUrl() + PRODUCT_SUMMARY_URL)
                    .queryParam("name", name);
            if (fromDate != null) {
                uriBuilder.queryParam("fromDate", fromDate);
            }
            if (toDate != null) {
                uriBuilder.queryParam("toDate", toDate);
            }

            return restTemplate.getForObject(uriBuilder.build().encode().toUri(), ProductSummaryDTO.class);
        } catch (RestClientException e) {
            log.error("Failed to summarize product: {}", name, e);
            throw new ApiException("Failed to summarize product: " + e.getMessage(), e);
        }
    }

    /**
     * Get the distinct product names, optionally narrowed down by a substring.
     *
     * @param search optional case-insensitive substring the name has to contain
     * @param limit  the maximum number of names to return
     * @return List of product names in alphabetical order
     * @throws ApiException if the API call fails
     */
    public List<String> getDistinctProductNames(String search, int limit) throws ApiException {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(getBaseUrl() + DISTINCT_PRODUCTS_URL)
                    .queryParam("limit", limit);
            if (search != null && !search.isBlank()) {
                uriBuilder.queryParam("search", search);
            }

            ResponseEntity<List<String>> response = restTemplate.exchange(
                    uriBuilder.build().encode().toUri(), HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                    });
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get distinct product names", e);
            throw new ApiException("Failed to retrieve product names: " + e.getMessage(), e);
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
                    .fromUriString(getBaseUrl() + PRODUCTS_URL)
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

