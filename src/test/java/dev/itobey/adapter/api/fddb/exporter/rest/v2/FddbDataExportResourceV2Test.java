package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit test for v2 Export API.
 */
@ExtendWith(MockitoExtension.class)
@Tag("v2")
class FddbDataExportResourceV2Test {

    @Mock
    private FddbDataService fddbDataService;

    @InjectMocks
    private FddbDataExportResourceV2 fddbDataExportResourceV2;

    @Test
    void testExportForTimerange() {
        DateRangeDTO mockRequest = new DateRangeDTO();
        ExportResultDTO mockResult = new ExportResultDTO();
        when(fddbDataService.exportForTimerange(mockRequest)).thenReturn(mockResult);

        ResponseEntity<ExportResultDTO> response = fddbDataExportResourceV2.exportForTimerange(mockRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResult, response.getBody());
    }

    @Test
    void testExportForDaysBack() {
        int days = 7;
        boolean includeToday = true;
        ExportResultDTO mockResult = new ExportResultDTO();
        when(fddbDataService.exportForDaysBack(days, includeToday)).thenReturn(mockResult);

        ResponseEntity<ExportResultDTO> response = fddbDataExportResourceV2.exportForDaysBack(days, includeToday);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResult, response.getBody());
    }
}

