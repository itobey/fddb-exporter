package dev.itobey.adapter.api.fddb.exporter.rest;

import dev.itobey.adapter.api.fddb.exporter.domain.ExportRequest;
import dev.itobey.adapter.api.fddb.exporter.domain.ExportResult;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
@Slf4j
@Validated
public class FddbDataResource {

    private final FddbDataService fddbDataService;

    private static final String DATE_PATTERN = "\\d{4}-\\d{2}-\\d{2}";

    /**
     * Retrieves all FDDB data entries.
     *
     * @return a ResponseEntity containing a list of all FDDB data entries
     */
    @GetMapping
    public ResponseEntity<List<FddbData>> findAllEntries() {
        List<FddbData> entries = fddbDataService.findAllEntries();
        return ResponseEntity.ok(entries);
    }

    /**
     * Retrieves FDDB data entries for the specified date.
     *
     * @param date the date for which to retrieve FDDB data entries, in the format YYYY-MM-DD
     * @return a ResponseEntity containing a list of FDDB data entries for the specified date, or a 400 Bad Request response if the date format is invalid
     */
    @GetMapping("/{date}")
    public ResponseEntity<?> findByDate(@PathVariable String date) {
        if (!isValidDate(date)) {
            return ResponseEntity.badRequest().body("Date must be in the format YYYY-MM-DD");
        }
        Optional<FddbData> entry = fddbDataService.findByDate(date);
        return entry.isPresent() ? ResponseEntity.ok(entry) : ResponseEntity.notFound().build();
    }

    /**
     * Search for FDDB data products by name.
     *
     * @param name the name to search for
     * @return a ResponseEntity containing a list of FDDB data matching the search criteria
     */
    @GetMapping("/products")
    public ResponseEntity<List<FddbData>> findByProduct(@RequestParam String name) {
        List<FddbData> products = fddbDataService.findByProduct(name);
        return ResponseEntity.ok(products);
    }

    /**
     * Export data for all days contained in the given timeframe as a batch.
     *
     * @param exportRequest the data which should be exported
     * @return HTTP 200 and 'ok' when everything went smoothly, error messages when it did not
     */
    @PostMapping
    public ResponseEntity<ExportResult> exportForTimerange(@Valid @RequestBody ExportRequest exportRequest) {
        ExportResult result = fddbDataService.exportForTimerange(exportRequest);
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
    public ResponseEntity<ExportResult> exportForDaysBack(
            @RequestParam int days,
            @RequestParam(defaultValue = "false") boolean includeToday) {
        ExportResult result = fddbDataService.exportForDaysBack(days, includeToday);
        return ResponseEntity.ok(result);
    }

    private boolean isValidDate(String date) {
        return Pattern.matches(DATE_PATTERN, date);
    }

}