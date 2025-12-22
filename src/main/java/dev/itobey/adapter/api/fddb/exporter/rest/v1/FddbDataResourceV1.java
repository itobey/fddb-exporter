package dev.itobey.adapter.api.fddb.exporter.rest.v1;

import dev.itobey.adapter.api.fddb.exporter.annotation.RequiresInfluxDb;
import dev.itobey.adapter.api.fddb.exporter.annotation.RequiresMongoDb;
import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.service.DataMigrationService;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Provides REST API endpoints for managing FDDB data.
 * <p>
 * This class handles various operations related to FDDB data, including:
 * - Retrieving all FDDB data entries
 * - Searching for FDDB data products by name
 * - Exporting FDDB data for a given timeframe or number of days
 * <p>
 * The API endpoints are mapped to the "/api/v1/fddbdata" path.
 *
 * @deprecated This is the v1 API compatibility layer. Please migrate to v2 API controllers:
 * {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataQueryResourceV2},
 * {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataExportResourceV2},
 * {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataStatsResourceV2},
 * {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataMigrationResourceV2}.
 * See docs/migration/v1-to-v2.md for migration guide.
 * This v1 API will be removed after 2026-06-30.
 */
// DEPRECATED: Remove after 2026-06-30 when all clients have migrated to v2
@Deprecated
@RestController
@RequestMapping("/api/v1/fddbdata")
@Slf4j
@Validated
@Tag(name = "FDDB Data (DEPRECATED v1)", description = "⚠️ DEPRECATED: Legacy v1 API - Will be removed after 2026-06-30. Please use v2 API endpoints.")
public class FddbDataResourceV1 {

    @Autowired
    private FddbDataService fddbDataService;
    @Autowired(required = false)
    private DataMigrationService dataMigrationService;

    private static final String DATE_PATTERN = "\\d{4}-\\d{2}-\\d{2}";

    /**
     * Retrieves all FDDB data entries.
     *
     * @return a ResponseEntity containing a list of all FDDB data entries
     * @deprecated Use {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataQueryResourceV2#findAllEntries()}
     */
    @Deprecated
    @GetMapping
    @RequiresMongoDb
    public ResponseEntity<List<FddbDataDTO>> findAllEntries() {
        log.debug("V1 (DEPRECATED): Retrieving all FDDB data entries");
        List<FddbDataDTO> entries = fddbDataService.findAllEntries();
        return ResponseEntity.ok()
                .header("X-API-Deprecated", "true")
                .header("Link", "</docs/migration/v1-to-v2.md>; rel=\"help\"; title=\"Migrate to v2\"")
                .body(entries);
    }

    /**
     * Retrieves FDDB data entries for the specified date.
     *
     * @param date the date for which to retrieve FDDB data entries, in the format YYYY-MM-DD
     * @return a ResponseEntity containing a list of FDDB data entries for the specified date, or a 400 Bad Request response if the date format is invalid
     * @deprecated Use {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataQueryResourceV2#findByDate(String)}
     */
    @Deprecated
    @GetMapping("/{date}")
    @RequiresMongoDb
    public ResponseEntity<?> findByDate(@PathVariable String date) {
        log.debug("V1 (DEPRECATED): Retrieving FDDB data for date: {}", date);
        if (!isValidDate(date)) {
            return ResponseEntity.badRequest().body("Date must be in the format YYYY-MM-DD");
        }
        Optional<FddbDataDTO> entry = fddbDataService.findByDate(date);
        return entry.isPresent()
                ? ResponseEntity.ok()
                .header("X-API-Deprecated", "true")
                .header("Link", "</docs/migration/v1-to-v2.md>; rel=\"help\"; title=\"Migrate to v2\"")
                .body(entry)
                : ResponseEntity.notFound().build();
    }

    /**
     * Search for FDDB data products by name.
     *
     * @param name the name to search for
     * @return a ResponseEntity containing a list of FDDB data matching the search criteria
     * @deprecated Use {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataQueryResourceV2#findByProduct(String)}
     */
    @Deprecated
    @GetMapping("/products")
    @RequiresMongoDb
    public ResponseEntity<List<ProductWithDateDTO>> findByProduct(@RequestParam String name) {
        log.debug("V1 (DEPRECATED): Searching for products with name: {}", name);
        List<ProductWithDateDTO> productWithDate = fddbDataService.findByProduct(name);
        return ResponseEntity.ok()
                .header("X-API-Deprecated", "true")
                .header("Link", "</docs/migration/v1-to-v2.md>; rel=\"help\"; title=\"Migrate to v2\"")
                .body(productWithDate);
    }

    /**
     * Export data for all days contained in the given timeframe as a batch.
     *
     * @param dateRangeDTO the date range which should be exported
     * @return HTTP 200 and 'ok' when everything went smoothly, error messages when it did not
     * @deprecated Use {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataExportResourceV2#exportForTimerange(DateRangeDTO)}
     */
    @Deprecated
    @PostMapping
    public ResponseEntity<ExportResultDTO> exportForTimerange(@Valid @RequestBody DateRangeDTO dateRangeDTO) {
        log.info("V1 (DEPRECATED): Exporting data for timerange: {} to {}",
                dateRangeDTO.getFromDate(), dateRangeDTO.getToDate());
        ExportResultDTO result = fddbDataService.exportForTimerange(dateRangeDTO);
        return ResponseEntity.ok()
                .header("X-API-Deprecated", "true")
                .header("Link", "</docs/migration/v1-to-v2.md>; rel=\"help\"; title=\"Migrate to v2\"")
                .body(result);
    }

    /**
     * Export data for the given amount of days.
     * example: /api/v1/fddbdata/export?days=2&includeToday=true
     * <p>
     * If includeToday is true the current day will be exported as well.
     *
     * @param days         the amount of days that should be exported
     * @param includeToday true, if the current day should be included as well
     * @return a list of saved and updated data points
     * @deprecated Use {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataExportResourceV2#exportForDaysBack(int, boolean)}
     */
    @Deprecated
    @GetMapping("/export")
    public ResponseEntity<ExportResultDTO> exportForDaysBack(
            @RequestParam int days,
            @RequestParam(defaultValue = "false") boolean includeToday) {
        log.info("V1 (DEPRECATED): Exporting data for {} days back (includeToday={})", days, includeToday);
        ExportResultDTO result = fddbDataService.exportForDaysBack(days, includeToday);
        return ResponseEntity.ok()
                .header("X-API-Deprecated", "true")
                .header("Link", "</docs/migration/v1-to-v2.md>; rel=\"help\"; title=\"Migrate to v2\"")
                .body(result);
    }

    /**
     * Get overall statistics for FDDB data.
     *
     * @return statistics including counts and other metrics
     * @deprecated Use {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataStatsResourceV2#getStats()}
     */
    @Deprecated
    @GetMapping("/stats")
    @RequiresMongoDb
    public ResponseEntity<StatsDTO> getStats() {
        log.debug("V1 (DEPRECATED): Retrieving FDDB data statistics");
        return ResponseEntity.ok()
                .header("X-API-Deprecated", "true")
                .header("Link", "</docs/migration/v1-to-v2.md>; rel=\"help\"; title=\"Migrate to v2\"")
                .body(fddbDataService.getStats());
    }

    /**
     * Get rolling averages for a specified date range.
     * example: /api/v1/fddbdata/stats/averages?fromDate=2024-01-01&toDate=2024-01-31
     *
     * @param dateRangeDTO the date range for which to calculate averages (including both from and to dates)
     * @return rolling averages for the specified date range
     * @deprecated Use {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataStatsResourceV2#getRollingAverages(DateRangeDTO)}
     */
    @Deprecated
    @GetMapping("/stats/averages")
    @RequiresMongoDb
    public ResponseEntity<?> getRollingAverages(@Valid DateRangeDTO dateRangeDTO) {
        log.debug("V1 (DEPRECATED): Calculating rolling averages for date range: {} to {}",
                dateRangeDTO.getFromDate(), dateRangeDTO.getToDate());
        try {
            RollingAveragesDTO result = fddbDataService.getRollingAverages(dateRangeDTO);
            return ResponseEntity.ok()
                    .header("X-API-Deprecated", "true")
                    .header("Link", "</docs/migration/v1-to-v2.md>; rel=\"help\"; title=\"Migrate to v2\"")
                    .body(result);
        } catch (IllegalArgumentException illegalArgumentException) {
            return ResponseEntity.badRequest().body(illegalArgumentException.getMessage());
        }
    }

    /**
     * Migrate all MongoDB entries to InfluxDB.
     *
     * @return the number of entries migrated
     * @deprecated Use {@link dev.itobey.adapter.api.fddb.exporter.rest.v2.FddbDataMigrationResourceV2#migrateMongoDbEntriesToInfluxDb()}
     */
    @Deprecated
    @PostMapping("/migrateToInfluxDb")
    @RequiresMongoDb
    @RequiresInfluxDb
    public ResponseEntity<String> migrateMongoDbEntriesToInfluxDb() {
        log.info("V1 (DEPRECATED): Starting migration from MongoDB to InfluxDB");
        int amountEntries = dataMigrationService.migrateMongoDbEntriesToInfluxDb();
        return ResponseEntity.ok()
                .header("X-API-Deprecated", "true")
                .header("Link", "</docs/migration/v1-to-v2.md>; rel=\"help\"; title=\"Migrate to v2\"")
                .body("Migrated " + amountEntries + " entries to InfluxDB");
    }

    private boolean isValidDate(String date) {
        return Pattern.matches(DATE_PATTERN, date);
    }

}

