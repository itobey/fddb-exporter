package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.ExportSummaryDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FddbExportToolsTest {

    @InjectMocks
    private FddbExportTools fddbExportTools;
    @Mock
    private FddbDataService fddbDataService;

    @Test
    void exportRange_shouldPassTheResolvedRangeAndSummarizeTheResult() {
        // given
        when(fddbDataService.exportForTimerange(any()))
                .thenReturn(result(List.of("2024-01-01", "2024-01-02"), List.of()));

        // when
        ExportSummaryDTO summary = fddbExportTools.exportRange("2024-01-01", "2024-01-02");

        // then
        ArgumentCaptor<DateRangeDTO> range = ArgumentCaptor.forClass(DateRangeDTO.class);
        verify(fddbDataService).exportForTimerange(range.capture());
        assertEquals("2024-01-01", range.getValue().getFromDate());
        assertEquals("2024-01-02", range.getValue().getToDate());

        assertEquals(LocalDate.of(2024, 1, 1), summary.getFromDate());
        assertEquals(LocalDate.of(2024, 1, 2), summary.getToDate());
        assertEquals(2, summary.getDaysRequested());
        assertEquals(2, summary.getSuccessCount());
        assertEquals(0, summary.getFailureCount());
        assertTrue(summary.getMessage().contains("2 day(s)"));
    }

    @Test
    void exportRange_shouldExplainThatAFailedDayIsUsuallyAnEmptyDiaryDay() {
        // given
        when(fddbDataService.exportForTimerange(any()))
                .thenReturn(result(List.of("2024-01-01"), List.of("2024-01-02")));

        // when
        ExportSummaryDTO summary = fddbExportTools.exportRange("2024-01-01", "2024-01-02");

        // then
        assertEquals(1, summary.getFailureCount());
        assertEquals(List.of("2024-01-02"), summary.getUnsuccessfulDays());
        assertTrue(summary.getMessage().contains("nothing was logged"),
                "a failed day reads as an error unless the message says otherwise: " + summary.getMessage());
    }

    @Test
    void exportRange_shouldResolveRelativeDates() {
        // given
        LocalDate today = LocalDate.now();
        when(fddbDataService.exportForTimerange(any())).thenReturn(result(List.of(), List.of()));

        // when
        ExportSummaryDTO summary = fddbExportTools.exportRange("6_days_ago", "today");

        // then
        assertEquals(today.minusDays(6), summary.getFromDate());
        assertEquals(today, summary.getToDate());
        assertEquals(7, summary.getDaysRequested());
    }

    @Test
    void exportRange_shouldRefuseARangeLongerThanTheCapWithoutScrapingAnything() {
        // when / then: 15 days, one over the cap
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> fddbExportTools.exportRange("2024-01-01", "2024-01-15"));
        assertTrue(exception.getMessage().contains("14 days per call"), exception.getMessage());
        // a refusal that does not name the uncapped path just makes the agent try 14 days at a time
        assertTrue(exception.getMessage().contains("REST API"), exception.getMessage());
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void exportRange_shouldAcceptExactlyTheCap() {
        // given
        when(fddbDataService.exportForTimerange(any())).thenReturn(result(List.of(), List.of()));

        // when
        ExportSummaryDTO summary = fddbExportTools.exportRange("2024-01-01", "2024-01-14");

        // then
        assertEquals(14, summary.getDaysRequested());
        verify(fddbDataService).exportForTimerange(any());
    }

    @Test
    void exportRange_shouldRejectAnInvertedRange() {
        // when / then
        assertThrows(DateTimeException.class, () -> fddbExportTools.exportRange("2024-01-02", "2024-01-01"));
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void exportDaysBack_shouldEndYesterdayUnlessTodayIsAskedFor() {
        // given
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(fddbDataService.exportForDaysBack(3, false)).thenReturn(result(List.of(), List.of()));

        // when
        ExportSummaryDTO summary = fddbExportTools.exportDaysBack(3, null);

        // then
        verify(fddbDataService).exportForDaysBack(3, false);
        assertEquals(yesterday, summary.getToDate());
        assertEquals(yesterday.minusDays(2), summary.getFromDate());
    }

    @Test
    void exportDaysBack_shouldIncludeTodayWhenAskedTo() {
        // given
        LocalDate today = LocalDate.now();
        when(fddbDataService.exportForDaysBack(2, true)).thenReturn(result(List.of(), List.of()));

        // when
        ExportSummaryDTO summary = fddbExportTools.exportDaysBack(2, true);

        // then
        assertEquals(today, summary.getToDate());
        assertEquals(today.minusDays(1), summary.getFromDate());
    }

    @Test
    void exportDaysBack_shouldRefuseMoreDaysThanTheCap() {
        // when / then: the MCP cap bites long before the service's own 1-365 bound does
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> fddbExportTools.exportDaysBack(15, false));
        assertTrue(exception.getMessage().contains("14 days per call"), exception.getMessage());
        assertTrue(exception.getMessage().contains("REST API"), exception.getMessage());
        assertThrows(DateTimeException.class, () -> fddbExportTools.exportDaysBack(365, false));
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void exportDaysBack_shouldRefuseFewerThanOneDay() {
        // when / then: the service would answer "Days back must be between 1 and 365", naming a
        // window this tool does not have
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> fddbExportTools.exportDaysBack(0, false));
        assertTrue(exception.getMessage().contains("at least one day"), exception.getMessage());
        assertThrows(DateTimeException.class, () -> fddbExportTools.exportDaysBack(-3, true));
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void exportMissingDays_shouldExportOnlyTheGaps() {
        // given
        passThroughExportLock();
        when(fddbDataService.getMissingDays(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5)))
                .thenReturn(List.of(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4)));
        when(fddbDataService.exportForTimerange(any()))
                .thenReturn(result(List.of("2024-01-02"), List.of()))
                .thenReturn(result(List.of(), List.of("2024-01-04")));

        // when
        ExportSummaryDTO summary = fddbExportTools.exportMissingDays("2024-01-01", "2024-01-05");

        // then one call per gap, so the three logged days are never re-fetched
        ArgumentCaptor<DateRangeDTO> ranges = ArgumentCaptor.forClass(DateRangeDTO.class);
        verify(fddbDataService, times(2)).exportForTimerange(ranges.capture());
        assertEquals(List.of("2024-01-02", "2024-01-04"),
                ranges.getAllValues().stream().map(DateRangeDTO::getFromDate).toList());

        assertEquals(2, summary.getDaysRequested());
        assertEquals(List.of("2024-01-02"), summary.getSuccessfulDays());
        assertEquals(List.of("2024-01-04"), summary.getUnsuccessfulDays());

        // one lock around the whole loop, or another caller slips in between two gaps
        verify(fddbDataService, times(1)).withExportLock(any());
    }

    @Test
    void exportMissingDays_shouldSayNothingWasMissingInsteadOfScraping() {
        // given
        when(fddbDataService.getMissingDays(any(), any())).thenReturn(List.of());

        // when
        ExportSummaryDTO summary = fddbExportTools.exportMissingDays("2024-01-01", "2024-01-05");

        // then
        assertEquals(0, summary.getSuccessCount());
        assertEquals(0, summary.getFailureCount());
        assertTrue(summary.getMessage().contains("already has an entry"));
        verify(fddbDataService, never()).exportForTimerange(any());
    }

    @Test
    void exportMissingDays_shouldRefuseToScrapeMoreGapsThanTheCap() {
        // given
        List<LocalDate> gaps = LocalDate.of(2024, 1, 1).datesUntil(LocalDate.of(2024, 5, 1)).toList();
        when(fddbDataService.getMissingDays(any(), any())).thenReturn(gaps);

        // when / then
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> fddbExportTools.exportMissingDays("2024-01-01", "2024-04-30"));
        assertTrue(exception.getMessage().contains(String.valueOf(gaps.size())));
        assertTrue(exception.getMessage().contains("14 days per call"), exception.getMessage());
        verify(fddbDataService, never()).exportForTimerange(any());
    }

    private ExportResultDTO result(List<String> successful, List<String> unsuccessful) {
        return new ExportResultDTO(successful, unsuccessful);
    }

    /**
     * The real lock lives in the service; here it only has to run what it is handed.
     */
    private void passThroughExportLock() {
        when(fddbDataService.withExportLock(any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
    }
}
