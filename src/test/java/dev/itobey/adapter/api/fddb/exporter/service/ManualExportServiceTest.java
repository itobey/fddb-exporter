package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import dev.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Test for {@link ManualExportService}
 */
@ExtendWith(MockitoExtension.class)
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
        timeframe = Timeframe.builder().from(123).to(456).build();
        fddbResponse = "<html>something</html>";
        fddbData = FddbData.builder().carbs(100).build();
    }

    @Test
    @SneakyThrows
    void exportBatch_whenPayloadValid_shouldAccessExporterService() {
        // given
        FddbBatchExport fddbBatchExport = FddbBatchExport.builder()
                .fromDate("2021-08-15")
                .toDate("2021-08-15")
                .build();
        Timeframe timeframe = Timeframe.builder().from(1628985600).to(1629072000).build();
        LocalDate localDate = LocalDate.of(2021, 8, 15);
        FddbData fddbData = FddbData.builder().fat(1).sugar(2).fiber(3).carbs(4).kcal(5).protein(6).date(new Date()).build();
        doReturn(fddbData).when(exportService).exportDataAndSaveToDb(timeframe);
        // when
        List<FddbData> actual = manualExportService.exportBatch(fddbBatchExport);
        // then
        verify(timeframeCalculator, Mockito.times(1)).calculateTimeframeFor(localDate);
        assertTrue(actual.size() == 1);
        assertTrue(actual.contains(fddbData));
    }

    @Test
    void exportBatch_whenPayloadInvalid_shouldThrowException() {
        // given
        FddbBatchExport fddbBatchExport = FddbBatchExport.builder()
                .fromDate("2021 08 15")
                .toDate("2021_08_15")
                .build();
        // when; then
        Exception exception = assertThrows(ManualExporterException.class, () -> manualExportService.exportBatch(fddbBatchExport));
        String expectedMessage = "payload of given times cannot be parsed";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    void exportBatch_whenFromIsAfterTo_shouldThrowException() {
        // given
        FddbBatchExport fddbBatchExport = FddbBatchExport.builder()
                .fromDate("2023-01-20")
                .toDate("2023-01-15")
                .build();
        // when; then
        Exception exception = assertThrows(ManualExporterException.class, () -> manualExportService.exportBatch(fddbBatchExport));
        String expectedMessage = "the 'from' date cannot be after the 'to' date";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    @SneakyThrows
    void exportBatchForDaysBack_whenTodayIncluded_shouldGenerateTimeframeAccordingly() {
        // given
        boolean includeToday = true;
        // when
        manualExportService.exportBatchForDaysBack(2, includeToday);
        // then
        verify(timeframeCalculator, times(1)).calculateTimeframeFor(LocalDate.now());
        verify(timeframeCalculator, times(1)).calculateTimeframeFor(LocalDate.now().minusDays(1));
        verify(timeframeCalculator, times(1)).calculateTimeframeFor(LocalDate.now().minusDays(2));
    }

    @Test
    @SneakyThrows
    void exportBatchForDaysBack_whenTodayNotIncluded_shouldGenerateTimeframeAccordingly() {
        // given
        boolean includeToday = false;
        // when
        manualExportService.exportBatchForDaysBack(2, includeToday);
        // then
        verify(timeframeCalculator, times(0)).calculateTimeframeFor(LocalDate.now());
        verify(timeframeCalculator, times(1)).calculateTimeframeFor(LocalDate.now().minusDays(1));
        verify(timeframeCalculator, times(1)).calculateTimeframeFor(LocalDate.now().minusDays(2));
    }
}
