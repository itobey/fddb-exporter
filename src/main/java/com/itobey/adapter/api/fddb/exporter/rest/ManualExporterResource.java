package com.itobey.adapter.api.fddb.exporter.rest;

import com.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import com.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import com.itobey.adapter.api.fddb.exporter.service.ManualExporterService;
import feign.Response;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * An endpoint to request manual exports opposed to scheduled ones.
 */
@Controller("/")
@Validated
@RequiredArgsConstructor
public class ManualExporterResource {

    private final ManualExporterService manualExporterService;

    /**
     * Export data for all days contained in the given timeframe as a batch.
     *
     * @param fddbBatchExport the data which should be exported
     * @return
     */
    @Post("/batch")
    public Single<String> batchExport(@RequestBody FddbBatchExport fddbBatchExport) {
        try {
            manualExporterService.exportBatch(fddbBatchExport);
        } catch (ManualExporterException e) {
            e.printStackTrace();
        }
        return Single.just("ok");
    }

}
