package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.annotation.RequiresMongoDb;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductRanking;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductSummaryDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.TopProductDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    @Operation(summary = "Search products by name", description = "Search for FDDB products by name across all dates, optionally filtered by specific days of the week, a date range and a maximum number of results")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductWithDateDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/products")
    @RequiresMongoDb
    public ResponseEntity<List<ProductWithDateDTO>> findByProduct(
            @Parameter(description = "Product name to search for", example = "Banana", required = true)
            @RequestParam String name,
            @Parameter(description = "Optional days of week to filter results (e.g., MONDAY, WEDNESDAY, FRIDAY)", example = "MONDAY,FRIDAY", required = false)
            @RequestParam(required = false) List<DayOfWeek> days,
            @Parameter(description = "Optional start date (inclusive), format: YYYY-MM-DD", example = "2024-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Optional end date (inclusive), format: YYYY-MM-DD", example = "2024-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "Optional maximum number of results", example = "100")
            @RequestParam(required = false) Integer limit) {
        log.debug("V2: Searching for products with name: {}, days: {}, range: {} to {}, limit: {}",
                name, days, fromDate, toDate, limit);
        List<ProductWithDateDTO> productWithDate = fddbDataService.findByProduct(name, days, fromDate, toDate, limit);
        return ResponseEntity.ok(productWithDate);
    }

    /**
     * Lists the distinct product names in the database.
     *
     * @param search optional case-insensitive substring the name has to contain
     * @param limit  the maximum number of names to return
     * @return a ResponseEntity containing the matching names in alphabetical order
     */
    @Operation(summary = "List distinct product names",
            description = "Lists the distinct product names in the database, so a fuzzy term (\"oats\") can be "
                    + "resolved to the exact, brand-prefixed name FDDB stores (\"Haferflocken kernig\").")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Distinct product names",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/products/distinct")
    @RequiresMongoDb
    public ResponseEntity<List<String>> findDistinctProductNames(
            @Parameter(description = "Optional case-insensitive substring the name has to contain", example = "hafer")
            @RequestParam(required = false) String search,
            @Parameter(description = "Maximum number of names to return", example = "100")
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        log.debug("V2: Listing distinct product names for search: {} (limit={})", search, limit);
        return ResponseEntity.ok(fddbDataService.findDistinctProductNames(search, limit));
    }

    /**
     * Aggregates every occurrence of the products matching a search term into a single summary.
     *
     * @param name     the product name to search for
     * @param fromDate optional start date (inclusive)
     * @param toDate   optional end date (inclusive)
     * @return a ResponseEntity containing the product summary
     */
    @Operation(summary = "Summarize a product",
            description = "Aggregates every occurrence of the products matching a search term: how often they were "
                    + "logged, first and last date, the totals they contributed and the weekday distribution.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product summary",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductSummaryDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/products/summary")
    @RequiresMongoDb
    public ResponseEntity<ProductSummaryDTO> getProductSummary(
            @Parameter(description = "Product name to search for", example = "Haferflocken", required = true)
            @RequestParam String name,
            @Parameter(description = "Optional start date (inclusive), format: YYYY-MM-DD", example = "2024-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Optional end date (inclusive), format: YYYY-MM-DD", example = "2024-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        log.debug("V2: Summarizing product {} for range {} to {}", name, fromDate, toDate);
        return ResponseEntity.ok(fddbDataService.getProductSummary(name, fromDate, toDate));
    }

    /**
     * Ranks products by how often they were logged or by the nutrient totals they contributed.
     *
     * @param by       the ranking criterion
     * @param fromDate optional start date (inclusive)
     * @param toDate   optional end date (inclusive)
     * @param limit    the maximum number of products to return
     * @return a ResponseEntity containing the ranked products, highest first
     */
    @Operation(summary = "List top products",
            description = "Ranks products by how often they were logged (FREQUENCY) or by the nutrient totals they "
                    + "contributed - \"what do I actually eat the most, and where do my calories come from?\"")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ranked products",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TopProductDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/products/top")
    @RequiresMongoDb
    public ResponseEntity<List<TopProductDTO>> getTopProducts(
            @Parameter(description = "Ranking criterion", example = "FREQUENCY")
            @RequestParam(defaultValue = "FREQUENCY") ProductRanking by,
            @Parameter(description = "Optional start date (inclusive), format: YYYY-MM-DD", example = "2024-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Optional end date (inclusive), format: YYYY-MM-DD", example = "2024-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "Maximum number of products to return", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int limit) {
        log.debug("V2: Listing top {} products by {} for range {} to {}", limit, by, fromDate, toDate);
        return ResponseEntity.ok(fddbDataService.getTopProducts(by, fromDate, toDate, limit));
    }

    private boolean isValidDate(String date) {
        return Pattern.matches(DATE_PATTERN, date);
    }
}

