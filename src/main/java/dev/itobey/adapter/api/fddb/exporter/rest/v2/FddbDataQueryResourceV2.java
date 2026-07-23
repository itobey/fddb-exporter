package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.annotation.RequiresMongoDb;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * V2 REST API for querying FDDB data entries.
 * <p>
 * Provides endpoints for:
 * - Retrieving all FDDB data entries
 * - Retrieving FDDB data by date
 * - Searching for products by name
 * <p>
 * The API endpoints are mapped to the "/api/v2/fddbdata" path.
 *
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/v2/fddbdata")
@Slf4j
@Validated
@RequiredArgsConstructor
@Tag(name = "FDDB Data Query", description = "Query FDDB nutrition data entries")
public class FddbDataQueryResourceV2 {

    private final FddbDataService fddbDataService;
    private static final String DATE_PATTERN = "\\d{4}-\\d{2}-\\d{2}";

    /**
     * Retrieves all FDDB data entries.
     *
     * @return a ResponseEntity containing a list of all FDDB data entries
     */
    @Operation(summary = "Get all FDDB data entries", description = "Retrieves all FDDB nutrition data entries from the database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FddbDataDTO.class))),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping
    @RequiresMongoDb
    public ResponseEntity<List<FddbDataDTO>> findAllEntries() {
        log.debug("V2: Retrieving all FDDB data entries");
        List<FddbDataDTO> entries = fddbDataService.findAllEntries();
        return ResponseEntity.ok(entries);
    }

    /**
     * Retrieves FDDB data entries for the specified date.
     *
     * @param date the date for which to retrieve FDDB data entries, in the format YYYY-MM-DD
     * @return a ResponseEntity containing FDDB data entries for the specified date,
     * or a 400 Bad Request response if the date format is invalid,
     * or 404 Not Found if no data exists for the date
     */
    @Operation(summary = "Get FDDB data for a specific date", description = "Retrieves FDDB data entries for the specified date")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data found for the specified date",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FddbDataDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date format", content = @Content),
            @ApiResponse(responseCode = "404", description = "No data found for the specified date", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/{date}")
    @RequiresMongoDb
    public ResponseEntity<?> findByDate(
            @Parameter(description = "Date in YYYY-MM-DD format", example = "2024-12-22", required = true)
            @PathVariable String date) {
        log.debug("V2: Retrieving FDDB data for date: {}", date);
        if (!isValidDate(date)) {
            return ResponseEntity.badRequest().body("Date must be in the format YYYY-MM-DD");
        }
        Optional<FddbDataDTO> entry = fddbDataService.findByDate(date);
        return entry.isPresent() ? ResponseEntity.ok(entry) : ResponseEntity.notFound().build();
    }

    /**
     * Retrieves all FDDB data entries within a date range.
     *
     * @param fromDate        the first date to include (inclusive)
     * @param toDate          the last date to include (inclusive)
     * @param includeProducts whether the product lists should be part of the response
     * @return a ResponseEntity containing the matching entries, oldest first
     */
    @Operation(summary = "Get FDDB data for a date range",
            description = "Retrieves all entries between two dates (both inclusive), oldest first. Product lists are "
                    + "omitted unless explicitly requested, since a long range with products is a very large response. "
                    + "The range is limited to " + FddbDataService.MAX_RANGE_DAYS + " days.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entries for the specified range",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FddbDataDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or too large date range", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/range")
    @RequiresMongoDb
    public ResponseEntity<List<FddbDataDTO>> findByDateRange(
            @Parameter(description = "Start date (inclusive), format: YYYY-MM-DD", example = "2024-12-01", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "End date (inclusive), format: YYYY-MM-DD", example = "2024-12-31", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @Parameter(description = "Whether to include the product list of each day", example = "false")
            @RequestParam(defaultValue = "false") boolean includeProducts) {
        log.debug("V2: Retrieving FDDB data for range {} to {} (includeProducts={})", fromDate, toDate, includeProducts);
        return ResponseEntity.ok(fddbDataService.findByDateRange(fromDate, toDate, includeProducts));
    }

    /**
     * Retrieves the FDDB data entries of the last N days, today included.
     *
     * @param days            how many days to look back
     * @param includeProducts whether the product lists should be part of the response
     * @return a ResponseEntity containing the matching entries, oldest first
     */
    @Operation(summary = "Get FDDB data for the last N days",
            description = "Convenience wrapper around the range endpoint for the most common query: the last N days, "
                    + "today included.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entries for the last N days",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FddbDataDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid number of days", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/recent")
    @RequiresMongoDb
    public ResponseEntity<List<FddbDataDTO>> findRecentDays(
            @Parameter(description = "Number of days to look back, today included", example = "7")
            @RequestParam(defaultValue = "7") int days,

            @Parameter(description = "Whether to include the product list of each day", example = "false")
            @RequestParam(defaultValue = "false") boolean includeProducts) {
        log.debug("V2: Retrieving FDDB data for the last {} days (includeProducts={})", days, includeProducts);
        return ResponseEntity.ok(fddbDataService.findRecentDays(days, includeProducts));
    }

    /**
     * Retrieves the most recent entry in the database.
     *
     * @return a ResponseEntity containing the newest entry, or 404 if the database is empty
     */
    @Operation(summary = "Get the most recent entry",
            description = "Retrieves the newest entry in the database - the cheapest way to find out how far the "
                    + "exported data reaches.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The most recent entry",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FddbDataDTO.class))),
            @ApiResponse(responseCode = "404", description = "No data at all", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/latest")
    @RequiresMongoDb
    public ResponseEntity<FddbDataDTO> findLatestEntry() {
        log.debug("V2: Retrieving the most recent FDDB data entry");
        return fddbDataService.findLatestEntry()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Search for FDDB data products by name, optionally filtered by days of the week.
     *
     * @param name the name to search for
     * @param days optional list of days of the week to filter results (e.g., MONDAY, WEDNESDAY)
     * @return a ResponseEntity containing a list of products matching the search criteria
     */
    @Operation(summary = "Search products by name", description = "Search for FDDB products by name across all dates, optionally filtered by specific days of the week")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductWithDateDTO.class))),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/products")
    @RequiresMongoDb
    public ResponseEntity<List<ProductWithDateDTO>> findByProduct(
            @Parameter(description = "Product name to search for", example = "Banana", required = true)
            @RequestParam String name,
            @Parameter(description = "Optional days of week to filter results (e.g., MONDAY, WEDNESDAY, FRIDAY)", example = "MONDAY,FRIDAY", required = false)
            @RequestParam(required = false) List<DayOfWeek> days) {
        log.debug("V2: Searching for products with name: {} and days: {}", name, days);
        List<ProductWithDateDTO> productWithDate = fddbDataService.findByProduct(name, days);
        return ResponseEntity.ok(productWithDate);
    }

    private boolean isValidDate(String date) {
        return Pattern.matches(DATE_PATTERN, date);
    }
}

