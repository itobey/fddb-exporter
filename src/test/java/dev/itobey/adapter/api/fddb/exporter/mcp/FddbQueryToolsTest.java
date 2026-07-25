package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.DayRangeResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.DayResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.ProductSearchResultDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FddbQueryToolsTest {

    @InjectMocks
    private FddbQueryTools fddbQueryTools;
    @Mock
    private FddbDataService fddbDataService;

    @Test
    void getDay_shouldReturnTheEntryWithoutItsDatabaseId() {
        // given
        FddbDataDTO entry = entryFor(LocalDate.of(2024, 12, 22));
        entry.setId("507f1f77bcf86cd799439011");
        when(fddbDataService.findByDate("2024-12-22")).thenReturn(Optional.of(entry));

        // when
        DayResultDTO result = fddbQueryTools.getDay("2024-12-22");

        // then
        assertTrue(result.isFound());
        assertEquals(LocalDate.of(2024, 12, 22), result.getDate());
        assertSame(entry, result.getEntry());
        assertNull(result.getEntry().getId());
        assertNull(result.getMessage());
    }

    @Test
    void getDay_shouldReportAMissingEntryAsNotFoundRatherThanFailing() {
        // given
        when(fddbDataService.findByDate("2024-12-22")).thenReturn(Optional.empty());

        // when
        DayResultDTO result = fddbQueryTools.getDay("2024-12-22");

        // then
        assertFalse(result.isFound());
        assertNull(result.getEntry());
        assertTrue(result.getMessage().contains("2024-12-22"));
    }

    @Test
    void getDay_shouldResolveRelativeDates() {
        // given
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(fddbDataService.findByDate(yesterday.toString())).thenReturn(Optional.empty());

        // when
        DayResultDTO result = fddbQueryTools.getDay("yesterday");

        // then
        assertEquals(yesterday, result.getDate());
        verify(fddbDataService).findByDate(yesterday.toString());
    }

    @Test
    void getDay_shouldRejectAnUnparseableDate() {
        // when / then
        assertThrows(DateTimeException.class, () -> fddbQueryTools.getDay("last tuesday"));
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void getDays_shouldEchoTheResolvedRangeAndCountTheEntriesItFound() {
        // given
        LocalDate from = LocalDate.of(2024, 12, 20);
        LocalDate to = LocalDate.of(2024, 12, 22);
        when(fddbDataService.findByDateRange(from, to, false))
                .thenReturn(new ArrayList<>(List.of(entryFor(from), entryFor(to))));

        // when
        DayRangeResultDTO result = fddbQueryTools.getDays("2024-12-20", "2024-12-22", null);

        // then
        assertEquals(from, result.getFromDate());
        assertEquals(to, result.getToDate());
        assertEquals(3, result.getDaysRequested());
        assertEquals(2, result.getEntryCount());
        assertFalse(result.isIncludeProducts());
        assertTrue(result.getEntries().stream().allMatch(entry -> entry.getId() == null));
    }

    @Test
    void getDays_shouldRequestProductsOnlyWhenAskedTo() {
        // given
        LocalDate date = LocalDate.of(2024, 12, 22);
        when(fddbDataService.findByDateRange(date, date, true))
                .thenReturn(new ArrayList<>(List.of(entryFor(date))));

        // when
        DayRangeResultDTO result = fddbQueryTools.getDays("2024-12-22", "2024-12-22", true);

        // then
        assertTrue(result.isIncludeProducts());
        verify(fddbDataService).findByDateRange(date, date, true);
    }

    @Test
    void getDays_shouldSurfaceTheRangeCapOfTheServiceLayer() {
        // given
        when(fddbDataService.findByDateRange(any(), any(), anyBoolean()))
                .thenThrow(new DateTimeException("The date range must not exceed 366 days"));

        // when
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> fddbQueryTools.getDays("2020-01-01", "2024-12-22", false));

        // then
        assertTrue(exception.getMessage().contains("366"));
    }

    @Test
    void searchProducts_shouldApplyTheDefaultLimitAndReportNoTruncation() {
        // given
        when(fddbDataService.findByProduct(eq("hafer"), eq(List.of()), isNull(), isNull(), eq(101)))
                .thenReturn(occurrences(4));

        // when
        ProductSearchResultDTO result = fddbQueryTools.searchProducts("hafer", null, null, null, null);

        // then
        assertEquals("hafer", result.getSearchTerm());
        assertEquals(100, result.getLimit());
        assertEquals(4, result.getResultCount());
        assertFalse(result.isTruncated());
        assertEquals(4, result.getResults().size());
    }

    @Test
    void searchProducts_shouldFlagTruncationInsteadOfSilentlyCuttingResultsOff() {
        // given
        when(fddbDataService.findByProduct(eq("hafer"), eq(List.of()), isNull(), isNull(), eq(4)))
                .thenReturn(occurrences(4));

        // when
        ProductSearchResultDTO result = fddbQueryTools.searchProducts("hafer", null, null, null, 3);

        // then
        assertTrue(result.isTruncated());
        assertEquals(3, result.getResultCount());
        assertEquals(3, result.getResults().size());
    }

    @Test
    void searchProducts_shouldCapTheLimitAndPassTheParsedFilters() {
        // given
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        when(fddbDataService.findByProduct(any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        // when
        ProductSearchResultDTO result =
                fddbQueryTools.searchProducts("hafer", List.of("monday", "FRIDAY"), "2024-01-01", "2024-12-31", 9000);

        // then
        assertEquals(500, result.getLimit());
        assertEquals(from, result.getFromDate());
        assertEquals(to, result.getToDate());
        verify(fddbDataService).findByProduct("hafer", List.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), from, to, 501);
    }

    @Test
    void searchProducts_shouldRejectAnUnknownDayOfWeek() {
        // when
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> fddbQueryTools.searchProducts("hafer", List.of("Montag"), null, null, null));

        // then
        assertTrue(exception.getMessage().contains("Montag"));
        verifyNoInteractions(fddbDataService);
    }

    private FddbDataDTO entryFor(LocalDate date) {
        FddbDataDTO entry = new FddbDataDTO();
        entry.setId("id-" + date);
        entry.setDate(date);
        return entry;
    }

    private List<ProductWithDateDTO> occurrences(int amount) {
        return IntStream.range(0, amount)
                .mapToObj(index -> new ProductWithDateDTO(LocalDate.of(2024, 12, 1).plusDays(index), new ProductDTO()))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
