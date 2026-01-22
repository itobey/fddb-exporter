package dev.itobey.adapter.api.fddb.exporter.rest.v1;

import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationInputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationOutputDto;
import dev.itobey.adapter.api.fddb.exporter.service.CorrelationService;
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
 * Provides REST API endpoints for correlation analysis.
 * <p>
 * The API endpoints are mapped to the "/api/v1/correlation" path.
 *
 * @deprecated This is the v1 API compatibility layer. Please migrate to
 * {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.CorrelationResourceV2}.
 * See docs/migration/v1-to-v2.md for migration guide.
 * This v1 API will be removed after 2026-06-30.
 */
// DEPRECATED: Remove after 2026-06-30 when all clients have migrated to v2
@Deprecated
@RestController
@RequestMapping("/api/v1/correlation")
@Slf4j
@Validated
@RequiredArgsConstructor
@Tag(name = "Correlation (DEPRECATED v1)", description = "⚠️ DEPRECATED: Legacy v1 API - Will be removed after 2026-06-30. Please use v2 API endpoints.")
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
public class CorrelationResourceV1 {

    private final CorrelationService correlationService;

    /**
     * Create a correlation analysis between two data series.
     *
     * @param correlationInputDto input data containing the series to correlate
     * @return correlation analysis result
     * @deprecated Use {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.CorrelationResourceV2#createCorrelation(CorrelationInputDto)}
     */
    @Deprecated
    @PostMapping
    public CorrelationOutputDto createCorrelation(@RequestBody CorrelationInputDto correlationInputDto) {
        log.debug("V1 (DEPRECATED): Creating correlation analysis");
        return correlationService.createCorrelation(correlationInputDto);
    }

}

