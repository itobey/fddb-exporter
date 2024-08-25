package dev.itobey.adapter.api.fddb.exporter.rest;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import dev.itobey.adapter.api.fddb.exporter.service.ManualExportService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualExporterResourceTest {

    @Mock
    private ManualExportService manualExportService;
    @InjectMocks
    private ManualExporterResource manualExporterResource;

    @Test
    @SneakyThrows
    void batchExport_Success() {
        FddbBatchExport batchExport = new FddbBatchExport();
        List<FddbData> expectedData = Arrays.asList(new FddbData(), new FddbData());

        when(manualExportService.exportBatch(batchExport)).thenReturn(expectedData);

        ResponseEntity<List<FddbData>> response = manualExporterResource.batchExport(batchExport);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedData, response.getBody());
        verify(manualExportService).exportBatch(batchExport);
    }

    @Test
    @SneakyThrows
    void batchExport_AuthenticationException() {
        FddbBatchExport batchExport = new FddbBatchExport();

        when(manualExportService.exportBatch(batchExport)).thenThrow(new AuthenticationException());

        ResponseEntity<List<FddbData>> response = manualExporterResource.batchExport(batchExport);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getBody());
        verify(manualExportService).exportBatch(batchExport);
    }

    @Test
    @SneakyThrows
    void batchExport_ManualExporterException() {
        FddbBatchExport batchExport = new FddbBatchExport();

        when(manualExportService.exportBatch(batchExport)).thenThrow(new ManualExporterException());

        ResponseEntity<List<FddbData>> response = manualExporterResource.batchExport(batchExport);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNull(response.getBody());
        verify(manualExportService).exportBatch(batchExport);
    }

    @Test
    @SneakyThrows
    void batchExportDaysBack_Success() {
        int days = 5;
        boolean includeToday = true;
        List<FddbData> expectedData = Arrays.asList(new FddbData(), new FddbData());

        when(manualExportService.exportBatchForDaysBack(days, includeToday)).thenReturn(expectedData);

        ResponseEntity<List<FddbData>> response = manualExporterResource.batchExportDaysBack(days, includeToday);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedData, response.getBody());
        verify(manualExportService).exportBatchForDaysBack(days, includeToday);
    }

    @Test
    @SneakyThrows
    void batchExportDaysBack_AuthenticationException() {
        int days = 5;
        boolean includeToday = true;

        when(manualExportService.exportBatchForDaysBack(days, includeToday)).thenThrow(new AuthenticationException());

        ResponseEntity<List<FddbData>> response = manualExporterResource.batchExportDaysBack(days, includeToday);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getBody());
        verify(manualExportService).exportBatchForDaysBack(days, includeToday);
    }

    @Test
    @SneakyThrows
    void batchExportDaysBack_ManualExporterException() {
        int days = 5;
        boolean includeToday = true;

        when(manualExportService.exportBatchForDaysBack(days, includeToday)).thenThrow(new ManualExporterException());

        ResponseEntity<List<FddbData>> response = manualExporterResource.batchExportDaysBack(days, includeToday);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNull(response.getBody());
        verify(manualExportService).exportBatchForDaysBack(days, includeToday);
    }
}