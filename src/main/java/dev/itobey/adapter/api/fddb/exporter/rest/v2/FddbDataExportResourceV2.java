package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.*;

/**
 * V2 REST API for exporting FDDB data.
 * <p>
 * Provides endpoints for:
 * - Exporting data for a specified date range
 * - Exporting data for a specified number of days back
 * <p>
 * The API endpoints are mapped to the "/api/v2/fddbdata/export" path.
 *
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/v2/fddbdata")
@Slf4j
@Validated
@RequiredArgsConstructor
@Tag(name = "FDDB Data Export", description = "Export FDDB data for specified date ranges")
public class FddbDataExportResourceV2 {

    private final FddbDataService fddbDataService;

    /**
     * Export data for all days contained in the given timeframe as a batch.
     *
     * @param dateRangeDTO the date range which should be exported
     * @return HTTP 200 and export result with saved and updated entries
     */
    @Operation(summary = "Export data for a date range", description = "Export FDDB data for all days in the specified timeframe")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Export completed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExportResultDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ExportResultDTO> exportForTimerange(@Valid @RequestBody DateRangeDTO dateRangeDTO) {
        log.info("V2: Exporting data for timerange: {} to {}",
                dateRangeDTO.getFromDate(), dateRangeDTO.getToDate());
        ExportResultDTO result = fddbDataService.exportForTimerange(dateRangeDTO);
        return ResponseEntity.ok(result);
    }

    /**
     * Export data for the given amount of days back from today.
     * <p>
     * Example: /api/v2/fddbdata/export?days=2&includeToday=true
     * <p>
     * If includeToday is true, the current day will be exported as well.
     *
     * @param days         the amount of days that should be exported
     * @param includeToday true, if the current day should be included as well
     * @return a list of saved and updated data points
     */
    @Operation(summary = "Export data for recent days", description = "Export FDDB data for a specified number of days back from today")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Export completed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExportResultDTO.class)))
    })
    @GetMapping("/export")
    public ResponseEntity<ExportResultDTO> exportForDaysBack(
            @Parameter(description = "Number of days to export", example = "7", required = true)
            @RequestParam int days,
            @Parameter(description = "Whether to include today in the export", example = "false")
            @RequestParam(defaultValue = "false") boolean includeToday) {
        log.info("V2: Exporting data for {} days back (includeToday={})", days, includeToday);
        ExportResultDTO result = fddbDataService.exportForDaysBack(days, includeToday);
        return ResponseEntity.ok(result);
    }
}

