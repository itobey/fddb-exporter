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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
     * Search for FDDB data products by name.
     *
     * @param name the name to search for
     * @return a ResponseEntity containing a list of products matching the search criteria
     */
    @Operation(summary = "Search products by name", description = "Search for FDDB products by name across all dates")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductWithDateDTO.class))),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/products")
    @RequiresMongoDb
    public ResponseEntity<List<ProductWithDateDTO>> findByProduct(
            @Parameter(description = "Product name to search for", example = "Banana", required = true)
            @RequestParam String name) {
        log.debug("V2: Searching for products with name: {}", name);
        List<ProductWithDateDTO> productWithDate = fddbDataService.findByProduct(name);
        return ResponseEntity.ok(productWithDate);
    }

    private boolean isValidDate(String date) {
        return Pattern.matches(DATE_PATTERN, date);
    }
}

