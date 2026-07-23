package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.annotation.RequiresMongoDb;
import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExtremeDirection;
import dev.itobey.adapter.api.fddb.exporter.dto.MacroSplitDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.WeekdayStatsDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

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

    /**
     * Get the top or bottom N days for a metric, optionally scoped to a date range.
     *
     * @param metric    the metric to rank days by
     * @param direction whether the highest or the lowest days are wanted
     * @param limit     the maximum number of days to return
     * @param fromDate  optional start date (inclusive)
     * @param toDate    optional end date (inclusive)
     * @return the matching days with their value for the metric, most extreme first
     */
    @Operation(summary = "Get extreme days",
            description = "Returns the top or bottom N days for a metric. Unlike the all-time single-day extremes in "
                    + "the overall statistics, this can be scoped to a date range and return more than one day.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Extreme days",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StatsDTO.DayStats.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/extremes")
    @RequiresMongoDb
    public ResponseEntity<?> getExtremeDays(
            @Parameter(description = "Metric to rank days by", example = "CALORIES")
            @RequestParam(defaultValue = "CALORIES") NutrientMetric metric,
            @Parameter(description = "Whether the highest or the lowest days are wanted", example = "HIGHEST")
            @RequestParam(defaultValue = "HIGHEST") ExtremeDirection direction,
            @Parameter(description = "Maximum number of days to return", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @Parameter(description = "Optional start date (inclusive), format: YYYY-MM-DD", example = "2024-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Optional end date (inclusive), format: YYYY-MM-DD", example = "2024-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        log.debug("V2: Retrieving {} {} days for {} in range {} to {}", limit, direction, metric, fromDate, toDate);
        try {
            return ResponseEntity.ok(fddbDataService.getExtremeDays(metric, direction, limit, fromDate, toDate));
        } catch (IllegalArgumentException illegalArgumentException) {
            return ResponseEntity.badRequest().body(illegalArgumentException.getMessage());
        }
    }

    /**
     * Get a time series of one metric, bucketed by day, ISO week or month.
     *
     * @param metric      the metric to trend
     * @param fromDate    the first date to include
     * @param toDate      the last date to include
     * @param granularity the bucket size
     * @return the buckets in chronological order
     */
    @Operation(summary = "Get a trend time series",
            description = "Builds a time series of one metric over a date range, bucketed by day, ISO week or month. "
                    + "Buckets without a single entry are omitted, so unlogged days never drag an average down.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Trend time series",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TrendPointDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/trend")
    @RequiresMongoDb
    public ResponseEntity<?> getTrend(
            @Parameter(description = "Metric to trend", example = "CALORIES")
            @RequestParam(defaultValue = "CALORIES") NutrientMetric metric,
            @Parameter(description = "Start date (inclusive), format: YYYY-MM-DD", example = "2024-01-01", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "End date (inclusive), format: YYYY-MM-DD", example = "2024-12-31", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "Bucket size", example = "WEEK")
            @RequestParam(defaultValue = "DAY") TrendGranularity granularity) {
        log.debug("V2: Retrieving {} trend for {} from {} to {}", granularity, metric, fromDate, toDate);
        try {
            return ResponseEntity.ok(fddbDataService.getTrend(metric, fromDate, toDate, granularity));
        } catch (IllegalArgumentException illegalArgumentException) {
            return ResponseEntity.badRequest().body(illegalArgumentException.getMessage());
        }
    }

    /**
     * Get averages grouped by day of the week.
     *
     * @param fromDate optional start date (inclusive)
     * @param toDate   optional end date (inclusive)
     * @return the averages per day of the week, Monday first
     */
    @Operation(summary = "Get a weekday breakdown",
            description = "Averages the daily totals grouped by day of the week - \"do my weekends wreck the average?\". "
                    + "Days of the week without a single entry are omitted.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Averages per day of the week",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WeekdayStatsDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/weekdays")
    @RequiresMongoDb
    public ResponseEntity<?> getWeekdayBreakdown(
            @Parameter(description = "Optional start date (inclusive), format: YYYY-MM-DD", example = "2024-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Optional end date (inclusive), format: YYYY-MM-DD", example = "2024-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        log.debug("V2: Retrieving weekday breakdown for range {} to {}", fromDate, toDate);
        try {
            return ResponseEntity.ok(fddbDataService.getWeekdayBreakdown(fromDate, toDate));
        } catch (IllegalArgumentException illegalArgumentException) {
            return ResponseEntity.badRequest().body(illegalArgumentException.getMessage());
        }
    }

    /**
     * Get the kcal-weighted share of energy from fat, carbs and protein.
     *
     * @param fromDate the first date to include
     * @param toDate   the last date to include
     * @return the macro split for the range
     */
    @Operation(summary = "Get the macro split",
            description = "Share of energy from fat, carbs and protein over a date range. The split is kcal-weighted "
                    + "(fat 9 kcal/g, carbs and protein 4 kcal/g), not gram-weighted.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Macro split",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MacroSplitDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/macro-split")
    @RequiresMongoDb
    public ResponseEntity<?> getMacroSplit(
            @Parameter(description = "Start date (inclusive), format: YYYY-MM-DD", example = "2024-01-01", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "End date (inclusive), format: YYYY-MM-DD", example = "2024-12-31", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        log.debug("V2: Retrieving macro split for range {} to {}", fromDate, toDate);
        try {
            return ResponseEntity.ok(fddbDataService.getMacroSplit(fromDate, toDate));
        } catch (IllegalArgumentException illegalArgumentException) {
            return ResponseEntity.badRequest().body(illegalArgumentException.getMessage());
        }
    }

    /**
     * List the days in a range that have no entry or an entry without a single calorie.
     *
     * @param fromDate the first date to check
     * @param toDate   the last date to check
     * @return the missing days in chronological order
     */
    @Operation(summary = "List missing days",
            description = "Lists every day in the range that has no entry at all or an entry without a single calorie "
                    + "- \"when did I forget to log?\"")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Missing days",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "Invalid date range", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/missing-days")
    @RequiresMongoDb
    public ResponseEntity<?> getMissingDays(
            @Parameter(description = "Start date (inclusive), format: YYYY-MM-DD", example = "2024-01-01", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "End date (inclusive), format: YYYY-MM-DD", example = "2024-12-31", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        log.debug("V2: Retrieving missing days for range {} to {}", fromDate, toDate);
        try {
            return ResponseEntity.ok(fddbDataService.getMissingDays(fromDate, toDate));
        } catch (IllegalArgumentException illegalArgumentException) {
            return ResponseEntity.badRequest().body(illegalArgumentException.getMessage());
        }
    }
}

