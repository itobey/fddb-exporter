package dev.itobey.adapter.api.fddb.exporter.rest;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import dev.itobey.adapter.api.fddb.exporter.service.ManualExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * An endpoint to request manual exports opposed to scheduled ones.
 */
@RestController("/")
@RequiredArgsConstructor
@Slf4j
public class ManualExporterResource {

    public static final String AUTH_EXCEPTION_MSG = "not logged in - batch export incomplete or unsuccessful";
    public static final String BATCH_EXPORT_MSG = "exception when handling batch export";
    private final ManualExportService manualExportService;

    /**
     * Export data for all days contained in the given timeframe as a batch.
     *
     * @param fddbBatchExport the data which should be exported
     * @return HTTP 200 and 'ok' when everything went smoothly, error messages when it did not
     */
    //TODO add unit tests
    @PostMapping("/batch")
    public List<FddbData> batchExport(@RequestBody FddbBatchExport fddbBatchExport) throws ManualExporterException {
        try {
            return manualExportService.exportBatch(fddbBatchExport);
        } catch (ManualExporterException e) {
            log.warn(BATCH_EXPORT_MSG);
            throw new ManualExporterException(BATCH_EXPORT_MSG);
        } catch (AuthenticationException e) {
            log.warn(AUTH_EXCEPTION_MSG);
            throw new ManualExporterException(AUTH_EXCEPTION_MSG);
        }
    }

    /**
     * Export data for the given amount of days.
     * example: /batch?days=2&includeToday=true
     * <p>
     * If includeToday is true the current day will be exported as well.
     *
     * @param params contains the Map with the values mentioned above
     * @return a list of saved and updated data points
     */
    //TODO add unit tests
    @GetMapping("/batch")
    public List<FddbData> batchExportDaysBack(@Nullable @RequestParam Map<String, String> params) throws ManualExporterException {
        int days = Integer.parseInt(params.get("days"));
        boolean includeToday = Boolean.parseBoolean(params.get("includeToday"));
        try {
            return manualExportService.exportBatchForDaysBack(days, includeToday);
        } catch (ManualExporterException e) {
            log.warn(BATCH_EXPORT_MSG);
            throw new ManualExporterException(BATCH_EXPORT_MSG);
        } catch (AuthenticationException e) {
            log.warn(AUTH_EXCEPTION_MSG);
            throw new ManualExporterException(AUTH_EXCEPTION_MSG);
        }
    }

}
