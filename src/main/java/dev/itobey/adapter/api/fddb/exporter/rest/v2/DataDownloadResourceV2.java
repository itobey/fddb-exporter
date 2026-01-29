package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.annotation.RequiresMongoDb;
import dev.itobey.adapter.api.fddb.exporter.dto.DownloadFormat;
import dev.itobey.adapter.api.fddb.exporter.service.DataDownloadService;
import dev.itobey.adapter.api.fddb.exporter.ui.service.DataDownloadClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * V2 REST API for downloading FDDB data in various formats.
 * <p>
 * Provides endpoints for:
 * - Downloading data as CSV or JSON
 * - Filtering by date range
 * - Including or excluding product details
 * <p>
 * The API endpoint is mapped to "/api/v2/fddbdata/download".
 *
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/v2/fddbdata")
@Slf4j
@Validated
@RequiredArgsConstructor
@Tag(name = "FDDB Data Download", description = "Download FDDB data in various formats")
public class DataDownloadResourceV2 {

    private final DataDownloadService dataDownloadService;

    /**
     * Download FDDB data in the specified format.
     *
     * @param fromDate         optional start date for filtering (inclusive)
     * @param toDate           optional end date for filtering (inclusive)
     * @param format           the download format (CSV or JSON)
     * @param includeProducts  whether to include product details (true) or just daily totals (false)
     * @param decimalSeparator the decimal separator for CSV format (comma or dot)
     * @return the data as a downloadable file
     */
    @Operation(summary = "Download FDDB data",
            description = "Download FDDB data as CSV or JSON. Optionally filter by date range and choose whether to include product details or just daily totals.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data downloaded successfully",
                    content = {
                            @Content(mediaType = "text/csv"),
                            @Content(mediaType = "application/json")
                    }),
            @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB not available", content = @Content)
    })
    @GetMapping("/download")
    @RequiresMongoDb
    public ResponseEntity<byte[]> downloadData(
            @Parameter(description = "Start date for filtering (inclusive), format: YYYY-MM-DD. If not provided, downloads from the beginning.", example = "2024-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "End date for filtering (inclusive), format: YYYY-MM-DD. If not provided, downloads until the most recent entry.", example = "2024-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @Parameter(description = "Download format: CSV or JSON", example = "CSV", required = true)
            @RequestParam DownloadFormat format,

            @Parameter(description = "Whether to include product details (true) or just daily totals (false)", example = "false")
            @RequestParam(defaultValue = "false") boolean includeProducts,

            @Parameter(description = "Decimal separator for CSV format: comma or dot", example = "comma")
            @RequestParam(defaultValue = "comma") String decimalSeparator) {

        log.info("V2: Downloading data: fromDate={}, toDate={}, format={}, includeProducts={}, decimalSeparator={}",
                fromDate, toDate, format, includeProducts, decimalSeparator);

        // Validate date range if both are provided
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().build();
        }

        // Validate decimal separator and convert descriptor to actual character
        String actualDecimalSeparator;
        if ("comma".equalsIgnoreCase(decimalSeparator)) {
            actualDecimalSeparator = ",";
        } else if ("dot".equalsIgnoreCase(decimalSeparator)) {
            actualDecimalSeparator = ".";
        } else {
            return ResponseEntity.badRequest().build();
        }

        byte[] data = dataDownloadService.downloadData(fromDate, toDate, format, includeProducts, actualDecimalSeparator);
        String filename = DataDownloadClient.generateDownloadFilename(fromDate, toDate, format, includeProducts);

        HttpHeaders headers = new HttpHeaders();
        // Set content type with UTF-8 charset for proper encoding
        String contentType = format.getContentType();
        if (format == DownloadFormat.CSV) {
            contentType += "; charset=UTF-8";
        }
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(data.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }
}

