package dev.itobey.adapter.api.fddb.exporter.rest;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import dev.itobey.adapter.api.fddb.exporter.service.ManualExportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * This class contains the resource for manual exports (as the usual way is to have the scheduler in
 * {@link dev.itobey.adapter.api.fddb.exporter.service.Scheduler} do the work.
 */
@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ManualExporterResource {

    private final ManualExportService manualExportService;

    /**
     * Export data for all days contained in the given timeframe as a batch.
     *
     * @param fddbBatchExport the data which should be exported
     * @return HTTP 200 and 'ok' when everything went smoothly, error messages when it did not
     */
    @PostMapping("/batch")
    public ResponseEntity<List<FddbData>> batchExport(@Valid @RequestBody FddbBatchExport fddbBatchExport) {
        try {
            List<FddbData> result = manualExportService.exportBatch(fddbBatchExport);
            return ResponseEntity.ok(result);
        } catch (AuthenticationException authenticationException) {
            log.error("Authentication error during batch export", authenticationException);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (ManualExporterException | RuntimeException exception) {
            log.error("Error during batch export", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export data for the given amount of days.
     * example: /batch?days=2&includeToday=true
     * <p>
     * If includeToday is true the current day will be exported as well.
     *
     * @param days         the amount of days that should be exported
     * @param includeToday true, if the current day should be included as well
     * @return a list of saved and updated data points
     */
    @GetMapping("/batch")
    public ResponseEntity<List<FddbData>> batchExportDaysBack(
            @RequestParam @Min(1) int days,
            @RequestParam(defaultValue = "false") boolean includeToday) {
        try {
            List<FddbData> result = manualExportService.exportBatchForDaysBack(days, includeToday);
            return ResponseEntity.ok(result);
        } catch (AuthenticationException authenticationException) {
            log.error("Authentication error during batch export", authenticationException);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (ManualExporterException manualExporterException) {
            log.error("Error during batch export", manualExporterException);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}