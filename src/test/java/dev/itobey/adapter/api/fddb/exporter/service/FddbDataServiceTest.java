package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.ExportRequest;
import dev.itobey.adapter.api.fddb.exporter.domain.ExportResult;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DateTimeException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FddbDataServiceTest {

    @InjectMocks
    private FddbDataService fddbDataService;

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
    void exportForTimerange_whenPayloadValid_shouldReturnSuccessfulDays() {
        // given
        ExportRequest exportRequest = new ExportRequest("2021-08-15", "2021-08-16");
        Timeframe timeframe = new Timeframe(1628985600, 1629072000);

        when(timeframeCalculator.calculateTimeframeFor(any(LocalDate.class))).thenReturn(timeframe);
        when(exportService.exportDataAndSaveToDb(timeframe)).thenReturn(mockFddbData);

        // when
        ExportResult result = fddbDataService.exportForTimerange(exportRequest);

        // then
        assertEquals(2, result.getSuccessfulDays().size());
        assertTrue(result.getSuccessfulDays().contains("2021-08-15"));
        assertTrue(result.getSuccessfulDays().contains("2021-08-16"));
        assertTrue(result.getUnsuccessfulDays().isEmpty());
        verify(timeframeCalculator, times(2)).calculateTimeframeFor(any(LocalDate.class));
        verify(exportService, times(2)).exportDataAndSaveToDb(timeframe);
    }

    @Test
    @SneakyThrows
    void exportForTimerange_whenExportFails_shouldReturnUnsuccessfulDays() {
        // given
        ExportRequest exportRequest = new ExportRequest("2021-08-15", "2021-08-16");
        Timeframe timeframe = new Timeframe(1628985600, 1629072000);

        when(timeframeCalculator.calculateTimeframeFor(any(LocalDate.class))).thenReturn(timeframe);
        when(exportService.exportDataAndSaveToDb(timeframe))
                .thenReturn(mockFddbData)
                .thenThrow(new ParseException("Failed to parse"));

        // when
        ExportResult result = fddbDataService.exportForTimerange(exportRequest);

        // then
        assertEquals(1, result.getSuccessfulDays().size());
        assertEquals(1, result.getUnsuccessfulDays().size());
        assertTrue(result.getSuccessfulDays().contains("2021-08-15"));
        assertTrue(result.getUnsuccessfulDays().contains("2021-08-16"));
        verify(timeframeCalculator, times(2)).calculateTimeframeFor(any(LocalDate.class));
        verify(exportService, times(2)).exportDataAndSaveToDb(timeframe);
    }

    @Test
    void exportForTimerange_whenFromIsAfterTo_shouldThrowException() {
        // given
        ExportRequest exportRequest = new ExportRequest("2023-01-20", "2023-01-15");

        // when & then
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> fddbDataService.exportForTimerange(exportRequest));
        assertEquals("The 'from' date cannot be after the 'to' date", exception.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "2, true",
            "2, false"
    })
    @SneakyThrows
    void exportForDaysBack_shouldGenerateTimeframeAccordingly(int days, boolean includeToday) {
        // given
        LocalDate today = LocalDate.now();
        LocalDate endDate = includeToday ? today : today.minusDays(1);
        LocalDate startDate = endDate.minusDays(days - 1);

        when(timeframeCalculator.calculateTimeframeFor(any(LocalDate.class))).thenReturn(mock(Timeframe.class));
        when(exportService.exportDataAndSaveToDb(any(Timeframe.class))).thenReturn(mockFddbData);

        // when
        ExportResult result = fddbDataService.exportForDaysBack(days, includeToday);

        // then
        assertEquals(days, result.getSuccessfulDays().size());
        assertTrue(result.getUnsuccessfulDays().isEmpty());
        verify(timeframeCalculator, times(days)).calculateTimeframeFor(any(LocalDate.class));
        verify(exportService, times(days)).exportDataAndSaveToDb(any(Timeframe.class));

        for (int i = 0; i < days; i++) {
            verify(timeframeCalculator).calculateTimeframeFor(startDate.plusDays(i));
        }
    }

    @Test
    void exportForDaysBack_whenDaysOutOfRange_shouldThrowException() {
        assertThrows(DateTimeException.class, () -> fddbDataService.exportForDaysBack(0, true));
        assertThrows(DateTimeException.class, () -> fddbDataService.exportForDaysBack(366, true));
    }
}