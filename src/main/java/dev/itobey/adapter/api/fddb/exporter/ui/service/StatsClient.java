package dev.itobey.adapter.api.fddb.exporter.ui.service;

import dev.itobey.adapter.api.fddb.exporter.dto.ExtremeDirection;
import dev.itobey.adapter.api.fddb.exporter.dto.MacroSplitDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.WeekdayStatsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * Client service for statistics-related API endpoints.
 */
@Service
@Slf4j
public class StatsClient {

    private static final String STATS_URL = "/api/v2/stats";
    private static final String AVERAGES_URL = "/api/v2/stats/averages?fromDate={fromDate}&toDate={toDate}";
    private static final String EXTREMES_URL = "/api/v2/stats/extremes";
    private static final String TREND_URL = "/api/v2/stats/trend";
    private static final String WEEKDAYS_URL = "/api/v2/stats/weekdays";
    private static final String MACRO_SPLIT_URL = "/api/v2/stats/macro-split?fromDate={fromDate}&toDate={toDate}";
    private static final String MISSING_DAYS_URL = "/api/v2/stats/missing-days";

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
    public RollingAveragesDTO getRollingAverages(String fromDate, String toDate) throws ApiException {
        try {
            String url = getBaseUrl() + AVERAGES_URL;
            return restTemplate.getForObject(url, RollingAveragesDTO.class, fromDate, toDate);
        } catch (RestClientException e) {
            log.error("Failed to get rolling averages", e);
            throw new ApiException("Failed to retrieve rolling averages: " + e.getMessage(), e);
        }
    }

    /**
     * Get the top or bottom N days for a metric, optionally scoped to a date range.
     *
     * @param metric    the metric to rank days by
     * @param direction whether the highest or the lowest days are wanted
     * @param limit     the maximum number of days to return
     * @param fromDate  optional start date in YYYY-MM-DD format
     * @param toDate    optional end date in YYYY-MM-DD format
     * @return List of DayStats, most extreme first
     * @throws ApiException if the API call fails
     */
    public List<StatsDTO.DayStats> getExtremeDays(NutrientMetric metric, ExtremeDirection direction, int limit,
                                                  String fromDate, String toDate) throws ApiException {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(getBaseUrl() + EXTREMES_URL)
                    .queryParam("metric", metric)
                    .queryParam("direction", direction)
                    .queryParam("limit", limit);
            addOptionalRange(uriBuilder, fromDate, toDate);

            ResponseEntity<List<StatsDTO.DayStats>> response = restTemplate.exchange(
                    uriBuilder.build().encode().toUri(), HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                    });
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get extreme days", e);
            throw new ApiException("Failed to retrieve extreme days: " + e.getMessage(), e);
        }
    }

    /**
     * Get a trend time series for a metric.
     *
     * @param metric      the metric to trend
     * @param fromDate    start date in YYYY-MM-DD format
     * @param toDate      end date in YYYY-MM-DD format
     * @param granularity the bucket size
     * @return List of TrendPointDTO in chronological order
     * @throws ApiException if the API call fails
     */
    public List<TrendPointDTO> getTrend(NutrientMetric metric, String fromDate, String toDate,
                                        TrendGranularity granularity) throws ApiException {
        try {
            URI uri = UriComponentsBuilder.fromUriString(getBaseUrl() + TREND_URL)
                    .queryParam("metric", metric)
                    .queryParam("fromDate", fromDate)
                    .queryParam("toDate", toDate)
                    .queryParam("granularity", granularity)
                    .build().encode().toUri();

            ResponseEntity<List<TrendPointDTO>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                    });
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get trend", e);
            throw new ApiException("Failed to retrieve trend: " + e.getMessage(), e);
        }
    }

    /**
     * Get averages grouped by day of the week.
     *
     * @param fromDate optional start date in YYYY-MM-DD format
     * @param toDate   optional end date in YYYY-MM-DD format
     * @return List of WeekdayStatsDTO, Monday first
     * @throws ApiException if the API call fails
     */
    public List<WeekdayStatsDTO> getWeekdayBreakdown(String fromDate, String toDate) throws ApiException {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(getBaseUrl() + WEEKDAYS_URL);
            addOptionalRange(uriBuilder, fromDate, toDate);

            ResponseEntity<List<WeekdayStatsDTO>> response = restTemplate.exchange(
                    uriBuilder.build().encode().toUri(), HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                    });
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get weekday breakdown", e);
            throw new ApiException("Failed to retrieve weekday breakdown: " + e.getMessage(), e);
        }
    }

    /**
     * Get the kcal-weighted share of energy from fat, carbs and protein.
     *
     * @param fromDate start date in YYYY-MM-DD format
     * @param toDate   end date in YYYY-MM-DD format
     * @return the MacroSplitDTO for the range
     * @throws ApiException if the API call fails
     */
    public MacroSplitDTO getMacroSplit(String fromDate, String toDate) throws ApiException {
        try {
            String url = getBaseUrl() + MACRO_SPLIT_URL;
            return restTemplate.getForObject(url, MacroSplitDTO.class, fromDate, toDate);
        } catch (RestClientException e) {
            log.error("Failed to get macro split", e);
            throw new ApiException("Failed to retrieve macro split: " + e.getMessage(), e);
        }
    }

    /**
     * List the days in a range that have no entry or an entry without a single calorie.
     *
     * @param fromDate start date in YYYY-MM-DD format
     * @param toDate   end date in YYYY-MM-DD format
     * @return the missing days in chronological order
     * @throws ApiException if the API call fails
     */
    public List<LocalDate> getMissingDays(String fromDate, String toDate) throws ApiException {
        try {
            URI uri = UriComponentsBuilder.fromUriString(getBaseUrl() + MISSING_DAYS_URL)
                    .queryParam("fromDate", fromDate)
                    .queryParam("toDate", toDate)
                    .build().encode().toUri();

            ResponseEntity<List<LocalDate>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                    });
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get missing days", e);
            throw new ApiException("Failed to retrieve missing days: " + e.getMessage(), e);
        }
    }

    private void addOptionalRange(UriComponentsBuilder uriBuilder, String fromDate, String toDate) {
        if (fromDate != null) {
            uriBuilder.queryParam("fromDate", fromDate);
        }
        if (toDate != null) {
            uriBuilder.queryParam("toDate", toDate);
        }
    }

    private String getBaseUrl() {
        return "http://localhost:8080";
    }
}

