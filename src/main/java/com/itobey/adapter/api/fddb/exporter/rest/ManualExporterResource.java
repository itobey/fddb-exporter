package com.itobey.adapter.api.fddb.exporter.rest;

import com.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import com.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import com.itobey.adapter.api.fddb.exporter.service.ManualExportService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.auth.AuthenticationException;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * An endpoint to request manual exports opposed to scheduled ones.
 */
@Controller("/")
@Validated
@RequiredArgsConstructor
@Slf4j
public class ManualExporterResource {

    private final ManualExportService manualExportService;

    /**
     * Export data for all days contained in the given timeframe as a batch.
     *
     * @param fddbBatchExport the data which should be exported
     * @return HTTP 200 and 'ok' when everything went smoothly, error messages when it did not
     */
    @Post("/batch")
    public Single<String> batchExport(@RequestBody FddbBatchExport fddbBatchExport) {
        try {
            manualExportService.exportBatch(fddbBatchExport);
        } catch (ManualExporterException e) {
            log.warn("exception when handling batch export");
            return Single.just("exception when handling batch export");
        } catch (AuthenticationException e) {
            log.warn("not logged in - batch export incomplete or unsuccessful");
            return Single.just("not logged in - batch export incomplete or unsuccessful");
        }
        return Single.just("ok");
    }

}
