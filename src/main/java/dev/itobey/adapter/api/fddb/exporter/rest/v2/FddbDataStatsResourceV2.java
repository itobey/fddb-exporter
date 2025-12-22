package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.annotation.RequiresMongoDb;
import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 REST API for FDDB data statistics.
 * <p>
 * Provides endpoints for:
 * - Retrieving overall statistics
 * - Calculating rolling averages for a date range
 * <p>
 * The API endpoints are mapped to the "/api/v2/stats" path.
 *
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/v2/stats")
@Slf4j
@Validated
@RequiredArgsConstructor
@Tag(name = "FDDB Data Statistics", description = "Statistics and analytics for FDDB data")
public class FddbDataStatsResourceV2 {

    private final FddbDataService fddbDataService;

    /**
     * Get overall statistics for FDDB data.
     *
     * @return statistics including counts and other metrics
     */
    @Operation(summary = "Get overall statistics", description = "Retrieves overall statistics for FDDB data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StatsDTO.class))),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping
    @RequiresMongoDb
    public ResponseEntity<StatsDTO> getStats() {
        log.debug("V2: Retrieving FDDB data statistics");
        return ResponseEntity.ok(fddbDataService.getStats());
    }

    /**
     * Get rolling averages for a specified date range.
     * <p>
     * Example: /api/v2/stats/averages?fromDate=2024-01-01&toDate=2024-01-31
     *
     * @param dateRangeDTO the date range for which to calculate averages (including both from and to dates)
     * @return rolling averages for the specified date range
     */
    @Operation(summary = "Get rolling averages", description = "Calculate rolling averages for a specified date range")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rolling averages calculated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RollingAveragesDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/averages")
    @RequiresMongoDb
    public ResponseEntity<?> getRollingAverages(@Valid DateRangeDTO dateRangeDTO) {
        log.debug("V2: Calculating rolling averages for date range: {} to {}",
                dateRangeDTO.getFromDate(), dateRangeDTO.getToDate());
        try {
            RollingAveragesDTO result = fddbDataService.getRollingAverages(dateRangeDTO);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException illegalArgumentException) {
            return ResponseEntity.badRequest().body(illegalArgumentException.getMessage());
        }
    }
}

