package dev.itobey.adapter.api.fddb.exporter.ui.service;

import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationInputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationOutputDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Client service for correlation analysis API endpoints.
 */
@Service
@Slf4j
public class CorrelationClient {

    private static final String CORRELATION_URL = "/api/v2/correlation";

    private final RestTemplate restTemplate;

    public CorrelationClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Create a correlation analysis.
     *
     * @param input the correlation input parameters
     * @return CorrelationOutputDto with results
     * @throws ApiException if the API call fails
     */
    public CorrelationOutputDto createCorrelation(CorrelationInputDto input) throws ApiException {
        try {
            return restTemplate.postForObject(getBaseUrl() + CORRELATION_URL, input, CorrelationOutputDto.class);
        } catch (RestClientException e) {
            log.error("Failed to create correlation", e);
            throw new ApiException("Failed to create correlation analysis: " + e.getMessage(), e);
        }
    }

    private String getBaseUrl() {
        return "http://localhost:8080";
    }
}

