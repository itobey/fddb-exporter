package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.ExtremeDaysResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.MissingDaysResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.TrendResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.WeekdayBreakdownResultDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FddbStatsToolsTest {

    @InjectMocks
    private FddbStatsTools fddbStatsTools;
    @Mock
    private FddbDataService fddbDataService;

    @Test
    void getStats_shouldDelegateToTheService() {
        // given
        StatsDTO stats = StatsDTO.builder().amountEntries(365).build();
        when(fddbDataService.getStats()).thenReturn(stats);

        // when
        StatsDTO result = fddbStatsTools.getStats();

        // then
        assertSame(stats, result);
    }

    @Test
    void getAverages_shouldPassTheResolvedRangeToTheService() {
        // given
        RollingAveragesDTO averages = RollingAveragesDTO.builder().build();
        when(fddbDataService.getRollingAverages(any(DateRangeDTO.class))).thenReturn(averages);

        // when
        RollingAveragesDTO result = fddbStatsTools.getAverages("2024-01-01", "2024-01-31");

        // then
        assertSame(averages, result);
        ArgumentCaptor<DateRangeDTO> range = ArgumentCaptor.forClass(DateRangeDTO.class);
        verify(fddbDataService).getRollingAverages(range.capture());
        assertEquals("2024-01-01", range.getValue().getFromDate());
        assertEquals("2024-01-31", range.getValue().getToDate());
    }

    @Test
    void getAverages_shouldResolveRelativeDates() {
        // given
        LocalDate today = LocalDate.now();
        when(fddbDataService.getRollingAverages(any(DateRangeDTO.class)))
                .thenReturn(RollingAveragesDTO.builder().build());

        // when
        fddbStatsTools.getAverages("6_days_ago", "today");

        // then
        ArgumentCaptor<DateRangeDTO> range = ArgumentCaptor.forClass(DateRangeDTO.class);
        verify(fddbDataService).getRollingAverages(range.capture());
        assertEquals(today.minusDays(6).toString(), range.getValue().getFromDate());
        assertEquals(today.toString(), range.getValue().getToDate());
    }

    @Test
    void getAverages_shouldRejectAnUnparseableDate() {
        // when / then
        assertThrows(DateTimeException.class, () -> fddbStatsTools.getAverages("beginning of the year", "today"));
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void getExtremeDays_shouldDefaultToTheTenHighestDaysOfTheWholeDiary() {
        // given
        when(fddbDataService.getExtremeDays(NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 10, null, null))
                .thenReturn(List.of(dayStats(LocalDate.of(2024, 3, 1), 3500)));

        // when
        ExtremeDaysResultDTO result = fddbStatsTools.getExtremeDays(NutrientMetric.CALORIES, null, null, null, null);

        // then
        assertEquals(ExtremeDirection.HIGHEST, result.getDirection());
        assertEquals(NutrientMetric.CALORIES, result.getMetric());
        assertEquals("kcal", result.getUnit());
        assertEquals(1, result.getResultCount());
        assertNull(result.getFromDate());
        assertNull(result.getToDate());
    }

    @Test
    void getExtremeDays_shouldReportGramsForEveryMetricButCalories() {
        // given
        when(fddbDataService.getExtremeDays(eq(NutrientMetric.PROTEIN), any(), anyInt(), any(), any()))
                .thenReturn(List.of());

        // when
        ExtremeDaysResultDTO result =
                fddbStatsTools.getExtremeDays(NutrientMetric.PROTEIN, ExtremeDirection.LOWEST, null, null, null);

        // then
        assertEquals("g", result.getUnit());
        assertEquals(ExtremeDirection.LOWEST, result.getDirection());
    }

    @Test
    void getExtremeDays_shouldCapTheLimitAndPassTheResolvedRange() {
        // given
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        when(fddbDataService.getExtremeDays(any(), any(), anyInt(), any(), any())).thenReturn(List.of());

        // when
        fddbStatsTools.getExtremeDays(NutrientMetric.FAT, ExtremeDirection.HIGHEST, 5000,
                "2024-01-01", "2024-12-31");

        // then
        verify(fddbDataService).getExtremeDays(NutrientMetric.FAT, ExtremeDirection.HIGHEST, 100, from, to);
    }

    @Test
    void getTrend_shouldDefaultToWeeklyBucketsAndSumTheLoggedDays() {
        // given
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 14);
        when(fddbDataService.getTrend(NutrientMetric.CALORIES, from, to, TrendGranularity.WEEK))
                .thenReturn(List.of(
                        TrendPointDTO.builder().bucket("2024-W01").dayCount(7).average(2000).build(),
                        TrendPointDTO.builder().bucket("2024-W02").dayCount(3).average(2400).build()));

        // when
        TrendResultDTO result = fddbStatsTools.getTrend(NutrientMetric.CALORIES, "2024-01-01", "2024-01-14", null);

        // then
        assertEquals(TrendGranularity.WEEK, result.getGranularity());
        assertEquals("kcal", result.getUnit());
        assertEquals(2, result.getBucketCount());
        // the incomplete second week has to stay visible, not be averaged away
        assertEquals(10, result.getLoggedDays());
    }

    @Test
    void getTrend_shouldPassTheRequestedGranularity() {
        // given
        LocalDate date = LocalDate.of(2024, 1, 1);
        when(fddbDataService.getTrend(any(), any(), any(), any())).thenReturn(List.of());

        // when
        fddbStatsTools.getTrend(NutrientMetric.SUGAR, "2024-01-01", "2024-01-01", TrendGranularity.MONTH);

        // then
        verify(fddbDataService).getTrend(NutrientMetric.SUGAR, date, date, TrendGranularity.MONTH);
    }

    @Test
    void getWeekdayBreakdown_shouldAcceptOpenBoundsAndSumTheLoggedDays() {
        // given
        when(fddbDataService.getWeekdayBreakdown(null, null)).thenReturn(List.of(
                WeekdayStatsDTO.builder().dayOfWeek(DayOfWeek.MONDAY).dayCount(52).build(),
                WeekdayStatsDTO.builder().dayOfWeek(DayOfWeek.SATURDAY).dayCount(50).build()));

        // when
        WeekdayBreakdownResultDTO result = fddbStatsTools.getWeekdayBreakdown(null, null);

        // then
        assertNull(result.getFromDate());
        assertNull(result.getToDate());
        assertEquals(102, result.getLoggedDays());
        assertEquals(2, result.getWeekdays().size());
    }

    @Test
    void listMissingDays_shouldCountBothSidesOfTheGapList() {
        // given
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 10);
        when(fddbDataService.getMissingDays(from, to))
                .thenReturn(List.of(LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 4)));

        // when
        MissingDaysResultDTO result = fddbStatsTools.listMissingDays("2024-01-01", "2024-01-10");

        // then
        assertEquals(10, result.getDaysChecked());
        assertEquals(2, result.getMissingCount());
        assertEquals(8, result.getLoggedCount());
        assertEquals(2, result.getMissingDays().size());
    }

    @Test
    void listMissingDays_shouldResolveRelativeDates() {
        // given
        LocalDate today = LocalDate.now();
        when(fddbDataService.getMissingDays(any(), any())).thenReturn(List.of());

        // when
        MissingDaysResultDTO result = fddbStatsTools.listMissingDays("29_days_ago", "yesterday");

        // then
        assertEquals(today.minusDays(29), result.getFromDate());
        assertEquals(today.minusDays(1), result.getToDate());
        assertEquals(29, result.getDaysChecked());
    }

    private StatsDTO.DayStats dayStats(LocalDate date, double total) {
        return StatsDTO.DayStats.builder().date(date).total(total).build();
    }
}
