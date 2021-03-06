package com.itobey.adapter.api.fddb.exporter;

import com.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import com.itobey.adapter.api.fddb.exporter.domain.FddbData;
import com.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import com.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import com.itobey.adapter.api.fddb.exporter.service.ExportService;
import com.itobey.adapter.api.fddb.exporter.service.ManualExportService;
import com.itobey.adapter.api.fddb.exporter.service.TimeframeCalculator;
import org.apache.http.auth.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link ManualExportService}
 */
class ManualExportServiceTest {

    @InjectMocks
    ManualExportService manualExportService;

    @Spy
    TimeframeCalculator timeframeCalculator;
    @Mock
    ExportService exportService;

    Timeframe timeframe;
    String fddbResponse;
    FddbData fddbData;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        timeframe = Timeframe.builder().from(123).to(456).build();
        fddbResponse = "<html>something</html>";
        fddbData = FddbData.builder().carbs(100).build();
    }

    @Test
    void exportBatch_whenPayloadValid_shouldAccessExporterService() throws ManualExporterException, AuthenticationException {
        // given
        FddbBatchExport fddbBatchExport = FddbBatchExport.builder()
                .fromDate("2021-08-15")
                .toDate("2021-08-15")
                .build();
        Timeframe timeframe = Timeframe.builder().from(1628985600).to(1629072000).build();
        // when
        manualExportService.exportBatch(fddbBatchExport);
        // then
        Mockito.verify(exportService, Mockito.times(1)).exportDataAndSaveToDb(timeframe);
    }

    @Test
    void exportBatch_whenPayloadInvalid_shouldThrowException() {
        // given
        FddbBatchExport fddbBatchExport = FddbBatchExport.builder()
                .fromDate("2021 08 15")
                .toDate("2021_08_15")
                .build();
        // when; then
        Exception exception = assertThrows(DateTimeParseException.class, () -> {
            manualExportService.exportBatch(fddbBatchExport);
        });
        String expectedMessage = "could not be parsed";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));
    }
}
