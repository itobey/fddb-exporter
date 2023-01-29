package com.itobey.adapter.api.fddb.exporter.rest;

import com.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import com.itobey.adapter.api.fddb.exporter.domain.FddbData;
import com.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import com.itobey.adapter.api.fddb.exporter.service.ManualExportService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.validation.Validated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.auth.AuthenticationException;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * An endpoint to request manual exports opposed to scheduled ones.
 */
@Controller("/")
@Validated
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
    @Post("/batch")
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
    @Get("/batch{?values*}")
    public List<FddbData> batchExportDaysBack(@Nullable @QueryValue("values") Map<String, String> params) throws ManualExporterException {
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
