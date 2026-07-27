package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.*;
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
    void getAverages_shouldPassTheResolvedRangeToTheServiceAndReportWhatItRestsOn() {
        // given
        StatsDTO.Averages averages = StatsDTO.Averages.builder().avgTotalCalories(2250).build();
        when(fddbDataService.countByDateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(12L);
        when(fddbDataService.getRollingAverages(any(DateRangeDTO.class)))
                .thenReturn(RollingAveragesDTO.builder().averages(averages).build());

        // when
        AveragesResultDTO result = fddbStatsTools.getAverages("2024-01-01", "2024-01-31");

        // then
        assertTrue(result.isFound());
        assertSame(averages, result.getAverages());
        assertNull(result.getMessage());
        // 12 logged days out of 31 is a different claim from an average over the month
        assertEquals(31, result.getDaysInRange());
        assertEquals(12, result.getLoggedDays());
        ArgumentCaptor<DateRangeDTO> range = ArgumentCaptor.forClass(DateRangeDTO.class);
        verify(fddbDataService).getRollingAverages(range.capture());
        assertEquals("2024-01-01", range.getValue().getFromDate());
        assertEquals("2024-01-31", range.getValue().getToDate());
    }

    @Test
    void getAverages_shouldResolveRelativeDates() {
        // given
        LocalDate today = LocalDate.now();
        when(fddbDataService.countByDateRange(any(), any())).thenReturn(7L);
        when(fddbDataService.getRollingAverages(any(DateRangeDTO.class)))
                .thenReturn(RollingAveragesDTO.builder().build());

        // when
        AveragesResultDTO result = fddbStatsTools.getAverages("6_days_ago", "today");

        // then
        ArgumentCaptor<DateRangeDTO> range = ArgumentCaptor.forClass(DateRangeDTO.class);
        verify(fddbDataService).getRollingAverages(range.capture());
        assertEquals(today.minusDays(6).toString(), range.getValue().getFromDate());
        assertEquals(today.toString(), range.getValue().getToDate());
        assertEquals(today.minusDays(6), result.getFromDate());
        assertEquals(today, result.getToDate());
    }

    @Test
    void getAverages_shouldAnswerRatherThanFailWhenNothingWasLogged() {
        // given: the averaging aggregation would raise "No data available for averaging" here
        when(fddbDataService.countByDateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
                .thenReturn(0L);

        // when
        AveragesResultDTO result = fddbStatsTools.getAverages("2024-01-01", "2024-01-07");

        // then: an empty range is the answer, not an error
        assertFalse(result.isFound());
        assertNull(result.getAverages());
        assertEquals(0, result.getLoggedDays());
        assertEquals(7, result.getDaysInRange());
        assertTrue(result.getMessage().contains("No day between 2024-01-01 and 2024-01-07"),
                result.getMessage());
        verify(fddbDataService, never()).getRollingAverages(any());
    }

    @Test
    void getAverages_shouldRejectAnUnparseableDate() {
        // when / then
        assertThrows(DateTimeException.class, () -> fddbStatsTools.getAverages("beginning of the year", "today"));
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void getAverages_shouldRejectAnInvertedRangeBeforeTouchingTheStore() {
        // when / then
        assertThrows(DateTimeException.class, () -> fddbStatsTools.getAverages("2024-01-31", "2024-01-01"));
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void getExtremeDays_shouldDefaultToTheTenHighestDaysOfTheWholeDiary() {
        // given: one more than the limit is asked for, so an overflow would be visible
        when(fddbDataService.getExtremeDays(NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 11, null, null))
                .thenReturn(List.of(dayStats(LocalDate.of(2024, 3, 1), 3500)));

        // when
        ExtremeDaysResultDTO result = fddbStatsTools.getExtremeDays(NutrientMetric.CALORIES, null, null, null, null);

        // then
        assertEquals(ExtremeDirection.HIGHEST, result.getDirection());
        assertEquals(NutrientMetric.CALORIES, result.getMetric());
        assertEquals("kcal", result.getUnit());
        assertEquals(1, result.getResultCount());
        assertEquals(10, result.getLimit());
        assertFalse(result.isTruncated());
        assertNull(result.getFromDate());
        assertNull(result.getToDate());
    }

    @Test
    void getExtremeDays_shouldSayWhenMoreDaysExistPastTheCut() {
        // given: the store has more days than were asked for
        when(fddbDataService.getExtremeDays(any(), any(), eq(4), any(), any()))
                .thenReturn(List.of(
                        dayStats(LocalDate.of(2024, 3, 1), 3500),
                        dayStats(LocalDate.of(2024, 3, 2), 3400),
                        dayStats(LocalDate.of(2024, 3, 3), 3300),
                        dayStats(LocalDate.of(2024, 3, 4), 3299)));

        // when
        ExtremeDaysResultDTO result =
                fddbStatsTools.getExtremeDays(NutrientMetric.CALORIES, null, 3, null, null);

        // then: the extra day is the signal, not part of the answer
        assertTrue(result.isTruncated());
        assertEquals(3, result.getLimit());
        assertEquals(3, result.getResultCount());
        assertEquals(3, result.getDays().size());
        assertEquals(LocalDate.of(2024, 3, 3), result.getDays().getLast().getDate());
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

        // then: capped at 100, plus the one extra that reveals a truncation
        verify(fddbDataService).getExtremeDays(NutrientMetric.FAT, ExtremeDirection.HIGHEST, 101, from, to);
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
        assertFalse(result.isTruncated());
        assertNull(result.getLimit());
    }

    @Test
    void listMissingDays_shouldCapTheDateListWithoutDistortingTheCounts() {
        // given: five years of a barely logged diary
        LocalDate from = LocalDate.of(2019, 1, 1);
        LocalDate to = LocalDate.of(2023, 12, 31);
        List<LocalDate> gaps = from.datesUntil(to.plusDays(1)).toList();
        when(fddbDataService.getMissingDays(from, to)).thenReturn(gaps);

        // when
        MissingDaysResultDTO result = fddbStatsTools.listMissingDays("2019-01-01", "2023-12-31");

        // then: the list is cut, the answer to "how many did I miss?" is not
        assertTrue(result.isTruncated());
        assertEquals(366, result.getMissingDays().size());
        assertEquals(366, result.getLimit());
        assertEquals(gaps.size(), result.getMissingCount());
        assertEquals(gaps.size(), result.getDaysChecked());
        assertEquals(0, result.getLoggedCount());
        // oldest first, so the cut end is the recent one the user can still fix
        assertEquals(from, result.getMissingDays().getFirst());
    }

    @Test
    void listMissingDays_shouldRejectAnInvertedRangeBeforeTouchingTheStore() {
        // when / then
        assertThrows(DateTimeException.class, () -> fddbStatsTools.listMissingDays("2024-01-31", "2024-01-01"));
        verifyNoInteractions(fddbDataService);
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

    @Test
    void getMacroSplit_shouldPassTheResolvedRangeToTheServiceAndReportWhatItRestsOn() {
        // given
        MacroSplitDTO split = MacroSplitDTO.builder().fatPercentage(34.5).build();
        when(fddbDataService.countByDateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(20L);
        when(fddbDataService.getMacroSplit(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(split);

        // when
        MacroSplitResultDTO result = fddbStatsTools.getMacroSplit("2024-01-01", "2024-01-31");

        // then
        assertTrue(result.isFound());
        assertSame(split, result.getSplit());
        assertEquals(31, result.getDaysInRange());
        assertEquals(20, result.getLoggedDays());
    }

    @Test
    void getMacroSplit_shouldAnswerRatherThanFailWhenNothingWasLogged() {
        // given
        when(fddbDataService.countByDateRange(any(), any())).thenReturn(0L);

        // when
        MacroSplitResultDTO result = fddbStatsTools.getMacroSplit("2024-01-01", "2024-01-07");

        // then
        assertFalse(result.isFound());
        assertNull(result.getSplit());
        assertEquals(0, result.getLoggedDays());
        assertTrue(result.getMessage().contains("nothing to split"), result.getMessage());
        verify(fddbDataService, never()).getMacroSplit(any(), any());
    }

    @Test
    void getMacroSplit_shouldRejectAnUnparseableDate() {
        // when / then
        assertThrows(DateTimeException.class, () -> fddbStatsTools.getMacroSplit("january", "today"));
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void getMacroSplit_shouldRejectAnInvertedRangeBeforeTouchingTheStore() {
        // when / then
        assertThrows(DateTimeException.class, () -> fddbStatsTools.getMacroSplit("2024-01-31", "2024-01-01"));
        verifyNoInteractions(fddbDataService);
    }

    private StatsDTO.DayStats dayStats(LocalDate date, double total) {
        return StatsDTO.DayStats.builder().date(date).total(total).build();
    }
}
