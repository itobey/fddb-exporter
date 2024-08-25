package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import dev.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManualExportServiceTest {

    @InjectMocks
    private ManualExportService manualExportService;

    @Mock
    private TimeframeCalculator timeframeCalculator;

    @Mock
    private ExportService exportService;

    private FddbData mockFddbData;

    @BeforeEach
    void setUp() {
        mockFddbData = mock(FddbData.class);
    }

    @Test
    @SneakyThrows
    void exportBatch_whenPayloadValid_shouldAccessExporterService() {
        // given
        FddbBatchExport fddbBatchExport = new FddbBatchExport("2021-08-15", "2021-08-15");
        Timeframe timeframe = new Timeframe(1628985600, 1629072000);
        LocalDate localDate = LocalDate.of(2021, 8, 15);

        when(timeframeCalculator.calculateTimeframeFor(localDate)).thenReturn(timeframe);
        when(exportService.exportDataAndSaveToDb(timeframe)).thenReturn(mockFddbData);

        // when
        List<FddbData> actual = manualExportService.exportBatch(fddbBatchExport);

        // then
        assertEquals(1, actual.size());
        assertTrue(actual.contains(mockFddbData));
        verify(timeframeCalculator).calculateTimeframeFor(localDate);
        verify(exportService).exportDataAndSaveToDb(timeframe);
    }

    @Test
    void exportBatch_whenFromIsAfterTo_shouldThrowException() {
        // given
        FddbBatchExport fddbBatchExport = new FddbBatchExport("2023-01-20", "2023-01-15");

        // when & then
        ManualExporterException exception = assertThrows(ManualExporterException.class,
                () -> manualExportService.exportBatch(fddbBatchExport));
        assertEquals("The 'from' date cannot be after the 'to' date", exception.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "2, true",
            "2, false"
    })
    @SneakyThrows
    void exportBatchForDaysBack_shouldGenerateTimeframeAccordingly(int days, boolean includeToday) {
        // given
        LocalDate today = LocalDate.now();
        LocalDate endDate = includeToday ? today : today.minusDays(1);
        LocalDate startDate = endDate.minusDays(days - 1);

        when(timeframeCalculator.calculateTimeframeFor(any(LocalDate.class))).thenReturn(mock(Timeframe.class));
        when(exportService.exportDataAndSaveToDb(any(Timeframe.class))).thenReturn(mockFddbData);

        // when
        List<FddbData> result = manualExportService.exportBatchForDaysBack(days, includeToday);

        // then
        assertEquals(days, result.size());
        verify(timeframeCalculator, times(days)).calculateTimeframeFor(any(LocalDate.class));
        verify(exportService, times(days)).exportDataAndSaveToDb(any(Timeframe.class));

        for (int i = 0; i < days; i++) {
            verify(timeframeCalculator).calculateTimeframeFor(startDate.plusDays(i));
        }
    }

    @Test
    void exportBatchForDaysBack_whenDaysOutOfRange_shouldThrowException() {
        assertThrows(ManualExporterException.class, () -> manualExportService.exportBatchForDaysBack(0, true));
        assertThrows(ManualExporterException.class, () -> manualExportService.exportBatchForDaysBack(366, true));
    }

}