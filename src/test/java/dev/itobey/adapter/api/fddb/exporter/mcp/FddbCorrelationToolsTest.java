package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationDetail;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationInputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationOutputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.Correlations;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.CorrelationResultDTO;
import dev.itobey.adapter.api.fddb.exporter.service.CorrelationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FddbCorrelationToolsTest {

    private static final List<String> KEYWORDS = List.of("hafer");
    private static final List<String> EVENTS = List.of("2024-03-04", "2024-03-19", "2024-04-02", "2024-04-11");

    @InjectMocks
    private FddbCorrelationTools fddbCorrelationTools;
    @Mock
    private CorrelationService correlationService;

    @Test
    void correlateProductsWithDates_shouldReportBothRatiosNextToTheirDenominators() {
        // given: the product was eaten on 20 days, 3 of which were event days
        when(correlationService.createCorrelation(any())).thenReturn(output(20,
                detail(3, 15.0, "2024-03-04", "2024-03-19", "2024-04-02"),
                detail(1, 5.0, "2024-04-10")));

        // when
        CorrelationResultDTO result =
                fddbCorrelationTools.correlateProductsWithDates(KEYWORDS, null, EVENTS, null);

        // then
        assertNull(result.getMessage());
        assertEquals(4, result.getEventDateCount());
        assertEquals(20, result.getDaysWithMatchingProduct());

        CorrelationResultDTO.Window sameDay = result.getSameDay();
        assertEquals(3, sameDay.getMatchedDays());
        assertEquals(15.0, sameDay.getPercentageOfProductDays());
        // 3 of the 4 events, which is the number a reader actually wants
        assertEquals(75.0, sameDay.getPercentageOfEvents());
        assertEquals(List.of(LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 19),
                LocalDate.of(2024, 4, 2)), sameDay.getMatchedDates());

        assertEquals(25.0, result.getOneDayBefore().getPercentageOfEvents());
        assertNotNull(result.getNote());
    }

    @Test
    void correlateProductsWithDates_shouldNotClaimAPerEventShareForTheAcrossWindows() {
        // given
        when(correlationService.createCorrelation(any())).thenReturn(output(20,
                detail(3, 15.0, "2024-03-04"), detail(1, 5.0, "2024-04-10")));

        // when
        CorrelationResultDTO result =
                fddbCorrelationTools.correlateProductsWithDates(KEYWORDS, null, EVENTS, null);

        // then: the across windows collapse consecutive days, so matchedDays is not a count of events
        assertNull(result.getAcross2Days().getPercentageOfEvents());
        assertNull(result.getAcross3Days().getPercentageOfEvents());
        assertNotNull(result.getAcross2Days().getPercentageOfProductDays());
    }

    @Test
    void correlateProductsWithDates_shouldSayNothingMatchedRatherThanReportZeroPercent() {
        // given
        CorrelationOutputDto empty = output(0, detail(0, 0), detail(0, 0));
        empty.setMatchedProducts(List.of());
        empty.setAmountMatchedProducts(0);
        when(correlationService.createCorrelation(any())).thenReturn(empty);

        // when
        CorrelationResultDTO result =
                fddbCorrelationTools.correlateProductsWithDates(List.of("quinoa"), null, EVENTS, null);

        // then: five windows of "0%" read like a finding, so none are reported at all
        assertTrue(result.getMessage().contains("quinoa"));
        assertNull(result.getSameDay());
        assertNull(result.getAcross3Days());
        assertEquals(0, result.getDaysWithMatchingProduct());
    }

    @Test
    void correlateProductsWithDates_shouldResolveRelativeDatesAndDropDuplicateEvents() {
        // given
        when(correlationService.createCorrelation(any())).thenReturn(output(5, detail(1, 20.0), detail(0, 0)));
        LocalDate today = LocalDate.now();

        // when
        CorrelationResultDTO result = fddbCorrelationTools.correlateProductsWithDates(
                KEYWORDS, List.of("riegel"), List.of("yesterday", today.minusDays(1).toString(), "today"),
                "30_days_ago");

        // then
        ArgumentCaptor<CorrelationInputDto> input = ArgumentCaptor.forClass(CorrelationInputDto.class);
        verify(correlationService).createCorrelation(input.capture());
        assertEquals(List.of(today.minusDays(1).toString(), today.toString()),
                input.getValue().getOccurrenceDates());
        assertEquals(today.minusDays(30).toString(), input.getValue().getStartDate());
        assertEquals(List.of("riegel"), input.getValue().getExclusionKeywords());
        // the duplicate must not inflate the denominator of the per-event share
        assertEquals(2, result.getEventDateCount());
    }

    @Test
    void correlateProductsWithDates_shouldCapTheListOfMatchedProductNames() {
        // given
        CorrelationOutputDto output = output(60, detail(1, 1.7), detail(0, 0));
        output.setMatchedProducts(IntStream.range(0, 60).mapToObj(index -> "Produkt " + index).toList());
        when(correlationService.createCorrelation(any())).thenReturn(output);

        // when
        CorrelationResultDTO result =
                fddbCorrelationTools.correlateProductsWithDates(KEYWORDS, null, EVENTS, null);

        // then: the names are capped, the counts are not
        assertTrue(result.isMatchedProductsTruncated());
        assertEquals(50, result.getMatchedProducts().size());
        assertEquals(60, result.getMatchedProductCount());
    }

    @Test
    void correlateProductsWithDates_shouldRejectAnEmptyRequest() {
        assertThrows(IllegalArgumentException.class, () ->
                fddbCorrelationTools.correlateProductsWithDates(List.of(), null, EVENTS, null));
        assertThrows(IllegalArgumentException.class, () ->
                fddbCorrelationTools.correlateProductsWithDates(List.of(" "), null, EVENTS, null));
        assertThrows(IllegalArgumentException.class, () ->
                fddbCorrelationTools.correlateProductsWithDates(KEYWORDS, null, List.of(), null));

        List<String> tooManyDates = IntStream.range(0, 400)
                .mapToObj(index -> LocalDate.of(2024, 1, 1).plusDays(index).toString())
                .toList();
        assertThrows(IllegalArgumentException.class, () ->
                fddbCorrelationTools.correlateProductsWithDates(KEYWORDS, null, tooManyDates, null));
    }

    private CorrelationOutputDto output(int daysWithProduct, CorrelationDetail sameDay,
                                        CorrelationDetail oneDayBefore) {
        Correlations correlations = new Correlations();
        correlations.setSameDay(sameDay);
        correlations.setOneDayBefore(oneDayBefore);
        correlations.setTwoDaysBefore(detail(0, 0));
        correlations.setAcross2Days(detail(sameDay.getMatchedDays() + oneDayBefore.getMatchedDays(), 20.0));
        correlations.setAcross3Days(detail(sameDay.getMatchedDays() + oneDayBefore.getMatchedDays(), 20.0));

        CorrelationOutputDto output = new CorrelationOutputDto();
        output.setCorrelations(correlations);
        output.setMatchedProducts(List.of("Alnatura Haferflocken kernig"));
        output.setAmountMatchedProducts(1);
        output.setAmountMatchedDates(daysWithProduct);
        return output;
    }

    private CorrelationDetail detail(int matchedDays, double percentage, String... matchedDates) {
        CorrelationDetail detail = new CorrelationDetail();
        detail.setMatchedDays(matchedDays);
        detail.setPercentage(percentage);
        detail.setMatchedDates(List.of(matchedDates));
        return detail;
    }
}
