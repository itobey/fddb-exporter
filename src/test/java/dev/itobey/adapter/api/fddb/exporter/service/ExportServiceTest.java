package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.adapter.FddbAdapter;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @InjectMocks
    private ExportService exportService;

    @Mock
    private FddbAdapter fddbAdapter;

    @Mock
    private FddbParserService fddbParserService;

    @Test
    @SneakyThrows
    void exportData_whenExportSuccessful_shouldReturnResult() {
        // Given
        long fromTimestamp = 1625097600L; // 2021-07-01 00:00:00 UTC
        Timeframe timeframe = new Timeframe(fromTimestamp, fromTimestamp + 86400); // One day later // 2021-07-01 00:00:00 UTC to 2021-07-02 00:00:00 UTC
        String mockResponse = "mock response data";
        FddbData mockParsedData = new FddbData();

        when(fddbAdapter.retrieveDataToTimeframe(timeframe)).thenReturn(mockResponse);
        when(fddbParserService.parseDiary(mockResponse)).thenReturn(mockParsedData);

        // When
        FddbData result = exportService.exportData(timeframe);

        // Then
        assertNotNull(result);
        assertEquals(LocalDate.of(2021, 7, 1), result.getDate());
    }

    @Test
    @SneakyThrows
    void exportData_whenAuthenticationExceptionThrown_shouldThrowException() {
        // Given
        Timeframe timeframe = new Timeframe(1625097600L, 1625184000L);
        when(fddbAdapter.retrieveDataToTimeframe(timeframe)).thenThrow(new AuthenticationException("Authentication failed"));

        // When & Then
        assertThrows(AuthenticationException.class, () -> exportService.exportData(timeframe));
        verifyNoInteractions(fddbParserService);
    }

    @Test
    @SneakyThrows
    void exportData_whenParseExceptionThrown_shouldThrowException() {
        // Given
        Timeframe timeframe = new Timeframe(1625097600L, 1625184000L);
        String mockResponse = "invalid response data";
        when(fddbAdapter.retrieveDataToTimeframe(timeframe)).thenReturn(mockResponse);
        when(fddbParserService.parseDiary(mockResponse)).thenThrow(new ParseException("Parsing failed"));

        // When & Then
        assertThrows(ParseException.class, () -> exportService.exportData(timeframe));
    }

}