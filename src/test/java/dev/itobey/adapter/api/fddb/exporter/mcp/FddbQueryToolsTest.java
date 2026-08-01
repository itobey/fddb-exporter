package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.*;
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

    @Test
    void listTopProducts_shouldDefaultToTheFrequencyRankingAndReportNoTruncation() {
        // given
        when(fddbDataService.getTopProducts(ProductRanking.FREQUENCY, null, null, 21))
                .thenReturn(topProducts(5));

        // when
        TopProductsResultDTO result = fddbQueryTools.listTopProducts(null, null, null, null);

        // then
        assertEquals(ProductRanking.FREQUENCY, result.getRankedBy());
        assertEquals(20, result.getLimit());
        assertEquals(5, result.getResultCount());
        assertFalse(result.isTruncated());
    }

    @Test
    void listTopProducts_shouldFlagTruncationInsteadOfSilentlyCuttingResultsOff() {
        // given
        when(fddbDataService.getTopProducts(ProductRanking.CALORIES, null, null, 4))
                .thenReturn(topProducts(4));

        // when
        TopProductsResultDTO result =
                fddbQueryTools.listTopProducts(ProductRanking.CALORIES, null, null, 3);

        // then
        assertTrue(result.isTruncated());
        assertEquals(3, result.getResultCount());
        assertEquals(3, result.getResults().size());
    }

    @Test
    void listTopProducts_shouldCapTheLimitAndPassTheResolvedRange() {
        // given
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        when(fddbDataService.getTopProducts(any(), any(), any(), anyInt())).thenReturn(List.of());

        // when
        TopProductsResultDTO result = fddbQueryTools.listTopProducts(
                ProductRanking.PROTEIN, "2024-01-01", "2024-12-31", 9000);

        // then
        assertEquals(100, result.getLimit());
        assertEquals(from, result.getFromDate());
        assertEquals(to, result.getToDate());
        verify(fddbDataService).getTopProducts(ProductRanking.PROTEIN, from, to, 101);
    }

    @Test
    void listTopProducts_shouldRejectAnUnparseableDate() {
        // when / then
        assertThrows(DateTimeException.class,
                () -> fddbQueryTools.listTopProducts(null, "beginning of the year", null, null));
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void getProductSummary_shouldPassTheResolvedRangeAndReturnTheSummary() {
        // given
        ProductSummaryDTO summary = ProductSummaryDTO.builder()
                .searchTerm("hafer")
                .timesEaten(42)
                .matchedProductNames(List.of("Haferflocken kernig"))
                .build();
        when(fddbDataService.getProductSummary("hafer", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
                .thenReturn(summary);

        // when
        ProductSummaryResultDTO result =
                fddbQueryTools.getProductSummary("hafer", "2024-01-01", "2024-12-31");

        // then
        assertTrue(result.isFound());
        assertSame(summary, result.getSummary());
        assertEquals(LocalDate.of(2024, 1, 1), result.getFromDate());
        assertEquals(LocalDate.of(2024, 12, 31), result.getToDate());
        assertNull(result.getMessage());
    }

    @Test
    void getProductSummary_shouldPointAtTheVocabularyLookupWhenNothingMatched() {
        // given zeroed totals read like a real answer, so an unmatched term has to say so
        when(fddbDataService.getProductSummary(eq("quinoa"), any(), any()))
                .thenReturn(ProductSummaryDTO.builder().searchTerm("quinoa").timesEaten(0).build());

        // when
        ProductSummaryResultDTO result = fddbQueryTools.getProductSummary("quinoa", null, null);

        // then
        assertFalse(result.isFound());
        assertNull(result.getSummary());
        assertTrue(result.getMessage().contains("list_distinct_products"));
    }

    @Test
    void listDistinctProducts_shouldReportThatItTruncatedTheResult() {
        // given
        when(fddbDataService.findDistinctProductNames("flocken", 3)).thenReturn(names(3));

        // when
        DistinctProductsResultDTO result = fddbQueryTools.listDistinctProducts("flocken", 2);

        // then
        assertTrue(result.isTruncated());
        assertEquals(2, result.getResultCount());
        assertEquals(2, result.getLimit());
        assertEquals("flocken", result.getSearchTerm());
    }

    @Test
    void listDistinctProducts_shouldCapTheLimit() {
        // given
        when(fddbDataService.findDistinctProductNames(null, 501)).thenReturn(names(1));

        // when
        DistinctProductsResultDTO result = fddbQueryTools.listDistinctProducts(null, 9000);

        // then
        assertEquals(500, result.getLimit());
        assertFalse(result.isTruncated());
        verify(fddbDataService).findDistinctProductNames(null, 501);
    }

    @Test
    void findDaysWithProducts_shouldReturnTheDaysNewestFirstAndCountTheOccurrencesBehindThem() {
        // given
        LocalDate first = LocalDate.of(2024, 1, 1);
        LocalDate second = LocalDate.of(2024, 1, 2);
        when(fddbDataService.findDaysWithProducts(eq(List.of("hafer")), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(
                        matchedDay(second, 1, "Haferdrink"),
                        matchedDay(first, 2, "Haferflocken kernig")));

        // when
        DaysWithProductsResultDTO result =
                fddbQueryTools.findDaysWithProducts(List.of("hafer"), null, null, null);

        // then two portions on one day are one day, but still two occurrences
        assertEquals(2, result.getDayCount());
        assertEquals(2, result.getMatchedDayCount());
        assertEquals(3, result.getOccurrenceCount());
        assertFalse(result.isTruncated());
        assertEquals(second, result.getDays().getFirst().getDate());
        assertEquals(List.of("Haferdrink"), result.getDays().getFirst().getProducts());
        assertEquals(List.of("Haferflocken kernig"), result.getDays().getLast().getProducts());
        // nothing was cut, so the totals are already known and cost no second query
        verify(fddbDataService, never()).countDaysWithProducts(any(), any(), any());
    }

    @Test
    void findDaysWithProducts_shouldPassTheExclusionsAndTheResolvedStartDate() {
        // given
        when(fddbDataService.findDaysWithProducts(any(), any(), any(), anyInt())).thenReturn(List.of());

        // when
        DaysWithProductsResultDTO result = fddbQueryTools.findDaysWithProducts(
                List.of("hafer"), List.of("keks"), "2024-01-01", null);

        // then: the cap goes into the query, one over the limit so an overflow is visible
        verify(fddbDataService).findDaysWithProducts(
                List.of("hafer"), List.of("keks"), LocalDate.of(2024, 1, 1), 101);
        assertEquals(LocalDate.of(2024, 1, 1), result.getStartDate());
        assertEquals(List.of("keks"), result.getExcludeKeywords());
    }

    @Test
    void findDaysWithProducts_shouldReportTheFullTotalsWhenItTruncates() {
        // given: the store returns limit + 1, which is how truncation is detected
        when(fddbDataService.findDaysWithProducts(any(), any(), any(), anyInt())).thenReturn(List.of(
                matchedDay(LocalDate.of(2024, 1, 2), 1, "Haferflocken"),
                matchedDay(LocalDate.of(2024, 1, 1), 1, "Haferflocken")));
        when(fddbDataService.countDaysWithProducts(List.of("hafer"), null, null))
                .thenReturn(ProductDayTotalsDTO.builder().dayCount(412).occurrenceCount(931).build());

        // when
        DaysWithProductsResultDTO result =
                fddbQueryTools.findDaysWithProducts(List.of("hafer"), null, null, 1);

        // then: "on how many days did I eat this?" is answerable from a truncated response
        assertTrue(result.isTruncated());
        assertEquals(1, result.getDayCount());
        assertEquals(1, result.getDays().size());
        assertEquals(412, result.getMatchedDayCount());
        assertEquals(931, result.getOccurrenceCount());
    }

    @Test
    void findDaysWithProducts_shouldRefuseToMatchEverything() {
        // when / then without a keyword this would return the whole diary
        assertThrows(IllegalArgumentException.class,
                () -> fddbQueryTools.findDaysWithProducts(List.of(), null, null, null));
        verifyNoInteractions(fddbDataService);
    }

    private ProductWithDateDTO occurrence(LocalDate date, String name) {
        ProductDTO product = new ProductDTO();
        product.setName(name);
        return new ProductWithDateDTO(date, product);
    }

    private DayWithProductsDTO matchedDay(LocalDate date, long occurrences, String... products) {
        return DayWithProductsDTO.builder()
                .date(date)
                .products(List.of(products))
                .occurrences(occurrences)
                .build();
    }

    private List<String> names(int amount) {
        return IntStream.range(0, amount)
                .mapToObj(index -> "product " + index)
                .collect(Collectors.toCollection(ArrayList::new));
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

    private List<TopProductDTO> topProducts(int amount) {
        return IntStream.range(0, amount)
                .mapToObj(index -> TopProductDTO.builder().name("product " + index).timesEaten(amount - index).build())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
