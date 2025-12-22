package dev.itobey.adapter.api.fddb.exporter.rest;

import dev.itobey.adapter.api.fddb.exporter.annotation.RequiresInfluxDb;
import dev.itobey.adapter.api.fddb.exporter.annotation.RequiresMongoDb;
import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.service.DataMigrationService;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
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
 */
@RestController
@RequestMapping("/api/v1/fddbdata")
@Slf4j
@Validated
public class FddbDataResource {

    @Autowired
    private FddbDataService fddbDataService;
    @Autowired(required = false)
    private DataMigrationService dataMigrationService;

    private static final String DATE_PATTERN = "\\d{4}-\\d{2}-\\d{2}";

    /**
     * Retrieves all FDDB data entries.
     *
     * @return a ResponseEntity containing a list of all FDDB data entries
     */
    @GetMapping
    @RequiresMongoDb
    public ResponseEntity<List<FddbDataDTO>> findAllEntries() {
        List<FddbDataDTO> entries = fddbDataService.findAllEntries();
        return ResponseEntity.ok(entries);
    }

    /**
     * Retrieves FDDB data entries for the specified date.
     *
     * @param date the date for which to retrieve FDDB data entries, in the format YYYY-MM-DD
     * @return a ResponseEntity containing a list of FDDB data entries for the specified date, or a 400 Bad Request response if the date format is invalid
     */
    @GetMapping("/{date}")
    @RequiresMongoDb
    public ResponseEntity<?> findByDate(@PathVariable String date) {
        if (!isValidDate(date)) {
            return ResponseEntity.badRequest().body("Date must be in the format YYYY-MM-DD");
        }
        Optional<FddbDataDTO> entry = fddbDataService.findByDate(date);
        return entry.isPresent() ? ResponseEntity.ok(entry) : ResponseEntity.notFound().build();
    }

    /**
     * Search for FDDB data products by name.
     *
     * @param name the name to search for
     * @return a ResponseEntity containing a list of FDDB data matching the search criteria
     */
    @GetMapping("/products")
    @RequiresMongoDb
    public ResponseEntity<List<ProductWithDateDTO>> findByProduct(@RequestParam String name) {
        List<ProductWithDateDTO> productWithDate = fddbDataService.findByProduct(name);
        return ResponseEntity.ok(productWithDate);
    }

    /**
     * Export data for all days contained in the given timeframe as a batch.
     *
     * @param dateRangeDTO the date range which should be exported
     * @return HTTP 200 and 'ok' when everything went smoothly, error messages when it did not
     */
    @PostMapping
    public ResponseEntity<ExportResultDTO> exportForTimerange(@Valid @RequestBody DateRangeDTO dateRangeDTO) {
        ExportResultDTO result = fddbDataService.exportForTimerange(dateRangeDTO);
        return ResponseEntity.ok(result);
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
     */
    @GetMapping("/export")
    public ResponseEntity<ExportResultDTO> exportForDaysBack(
            @RequestParam int days,
            @RequestParam(defaultValue = "false") boolean includeToday) {
        ExportResultDTO result = fddbDataService.exportForDaysBack(days, includeToday);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    @RequiresMongoDb
    public ResponseEntity<StatsDTO> getStats() {
        return ResponseEntity.ok(fddbDataService.getStats());
    }

    /**
     * Get rolling averages for a specified date range.
     * example: /api/v1/fddbdata/stats/averages?fromDate=2024-01-01&toDate=2024-01-31
     *
     * @param dateRangeDTO the date range for which to calculate averages (including both from and to dates)
     * @return rolling averages for the specified date range
     */
    @GetMapping("/stats/averages")
    @RequiresMongoDb
    public ResponseEntity<?> getRollingAverages(@Valid DateRangeDTO dateRangeDTO) {
        try {
            RollingAveragesDTO result = fddbDataService.getRollingAverages(dateRangeDTO);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException illegalArgumentException) {
            return ResponseEntity.badRequest().body(illegalArgumentException.getMessage());
        }
    }

    @PostMapping("/migrateToInfluxDb")
    @RequiresMongoDb
    @RequiresInfluxDb
    public ResponseEntity<String> migrateMongoDbEntriesToInfluxDb() {
        int amountEntries = dataMigrationService.migrateMongoDbEntriesToInfluxDb();
        return ResponseEntity.ok("Migrated " + amountEntries + " entries to InfluxDB");
    }

    private boolean isValidDate(String date) {
        return Pattern.matches(DATE_PATTERN, date);
    }

}