package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationInputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationOutputDto;
import dev.itobey.adapter.api.fddb.exporter.service.CorrelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 REST API for correlation analysis.
 * <p>
 * Provides endpoints for calculating correlations between different data series.
 * <p>
 * The API endpoints are mapped to the "/api/v2/correlation" path.
 *
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/v2/correlation")
@Slf4j
@Validated
@RequiredArgsConstructor
@Tag(name = "Correlation Analysis", description = "Correlation analysis between data series")
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
public class CorrelationResourceV2 {

    private final CorrelationService correlationService;

    /**
     * Create a correlation analysis between two data series.
     *
     * @param correlationInputDto input data containing the series to correlate
     * @return correlation analysis result
     */
    @Operation(summary = "Create correlation analysis", description = "Calculate correlation between two data series")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Correlation calculated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CorrelationOutputDto.class)))
    })
    @PostMapping
    public CorrelationOutputDto createCorrelation(@RequestBody CorrelationInputDto correlationInputDto) {
        log.debug("V2: Creating correlation analysis");
        return correlationService.createCorrelation(correlationInputDto);
    }
}

