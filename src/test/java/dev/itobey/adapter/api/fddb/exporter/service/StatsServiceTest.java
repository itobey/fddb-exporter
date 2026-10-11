package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.StatsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private StatsRepository statsRepository;

    private StatsService statsService() {
        return new StatsService(providerOf(statsRepository));
    }

    /**
     * The service resolves its optional dependency once in the constructor, so the tests only need a
     * provider that hands back the mock - or nothing at all, for the MongoDB-disabled case.
     */
    @SuppressWarnings("unchecked")
    private ObjectProvider<StatsRepository> providerOf(StatsRepository repository) {
        ObjectProvider<StatsRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(repository);
        return provider;
    }

    @Test
    void getStats_shouldGatherAllData() {
        // given
        when(statsRepository.countAll()).thenReturn(100L);
        when(statsRepository.findFirstEntryDate()).thenReturn(LocalDate.of(2023, 1, 1));
        when(statsRepository.findLastEntryDate()).thenReturn(LocalDate.of(2023, 6, 1));
        when(statsRepository.averages(null, null)).thenReturn(Optional.of(StatsDTO.Averages.builder()
                .avgTotalCalories(2000)
                .avgTotalFat(70)
                .avgTotalCarbs(250)
                .avgTotalSugar(50)
                .avgTotalProtein(100)
                .avgTotalFibre(30)
                .build()));
        when(statsRepository.countUniqueProducts()).thenReturn(42L);
        when(statsRepository.countTotalProducts()).thenReturn(120L);
        when(statsRepository.findHighestDayPerMetric()).thenReturn(highestDayForEveryMetric(
                StatsDTO.DayStats.builder().date(LocalDate.of(2023, 5, 1)).total(3000).build()));
        when(statsRepository.findLoggedDates(any(), any())).thenReturn(Set.of());

        // when
        StatsDTO result = statsService().getStats();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmountEntries()).isEqualTo(100L);
        assertThat(result.getFirstEntryDate()).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(result.getLastEntryDate()).isEqualTo(LocalDate.of(2023, 6, 1));
        assertThat(result.getUniqueProducts()).isEqualTo(42L);
        assertThat(result.getTotalProducts()).isEqualTo(120L);
        assertThat(result.getAverageTotals().getAvgTotalCalories()).isEqualTo(2000.0);
        assertThat(result.getHighestCaloriesDay().getTotal()).isEqualTo(3000.0);
        assertThat(result.getHighestSugarDay().getDate()).isEqualTo(LocalDate.of(2023, 5, 1));
    }

    @Test
    void getStats_shouldReportFullCoverage_whenTheOnlyEntryIsToday() {
        // given: a brand-new installation on its first day - dividing by zero days produced Infinity, which
        // is not valid JSON and rendered as "Infinity%" on the dashboard
        LocalDate today = LocalDate.now();
        when(statsRepository.countAll()).thenReturn(1L);
        when(statsRepository.findFirstEntryDate()).thenReturn(today);
        when(statsRepository.averages(null, null)).thenReturn(Optional.of(StatsDTO.Averages.builder().build()));
        when(statsRepository.findHighestDayPerMetric()).thenReturn(Map.of());
        when(statsRepository.findLoggedDates(any(), any())).thenReturn(Set.of(today));

        // when
        StatsDTO result = statsService().getStats();

        // then
        assertThat(result.getEntryPercentage()).isEqualTo(100.0);
    }

    @Test
    void getStats_shouldDeriveMissingDayCountAndStreaksFromOneQuery() {
        // given
        LocalDate today = LocalDate.now();
        LocalDate firstEntryDate = today.minusDays(4);
        when(statsRepository.countAll()).thenReturn(3L);
        when(statsRepository.findFirstEntryDate()).thenReturn(firstEntryDate);
        when(statsRepository.averages(null, null)).thenReturn(Optional.of(StatsDTO.Averages.builder().build()));
        when(statsRepository.findHighestDayPerMetric()).thenReturn(Map.of());
        // logged: -4, -3, -1 - so -2 is a gap and today is not logged yet
        when(statsRepository.findLoggedDates(firstEntryDate, today)).thenReturn(Set.of(
                today.minusDays(4), today.minusDays(3), today.minusDays(1)));

        // when
        StatsDTO result = statsService().getStats();

        // then
        assertThat(result.getMostRecentMissingDay()).isEqualTo(today.minusDays(2));
        assertThat(result.getMissingDaysCount()).isEqualTo(1L);
        // yesterday is logged and today being unlogged does not break the streak yet
        assertThat(result.getCurrentStreak()).isEqualTo(1);
        assertThat(result.getLongestStreak()).isEqualTo(2);
    }

    @Test
    void getStats_shouldReportCoverageAsNull_whenTheCoverageQueryFails() {
        // given: the rest of the statistics still works, only the coverage query fails - it must not
        // claim MongoDB is unconfigured, and it must not fail the whole call
        LocalDate today = LocalDate.now();
        when(statsRepository.countAll()).thenReturn(3L);
        when(statsRepository.findFirstEntryDate()).thenReturn(today.minusDays(4));
        when(statsRepository.averages(null, null)).thenReturn(Optional.of(StatsDTO.Averages.builder().build()));
        when(statsRepository.findHighestDayPerMetric()).thenReturn(Map.of());
        when(statsRepository.findLoggedDates(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("timed out"));

        // when
        StatsDTO result = statsService().getStats();

        // then
        assertThat(result.getAmountEntries()).isEqualTo(3L);
        assertThat(result.getMostRecentMissingDay()).isNull();
        assertThat(result.getMissingDaysCount()).isNull();
        assertThat(result.getCurrentStreak()).isNull();
        assertThat(result.getLongestStreak()).isNull();
    }

    @Test
    void getStats_shouldReturnZerosAndNullsForEmptyDatabase() {
        // given
        when(statsRepository.countAll()).thenReturn(0L);

        // when
        StatsDTO result = statsService().getStats();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmountEntries()).isEqualTo(0L);
        assertThat(result.getFirstEntryDate()).isNull();
        assertThat(result.getEntryPercentage()).isEqualTo(0.0);
        assertThat(result.getUniqueProducts()).isEqualTo(0L);
        assertThat(result.getAverageTotals()).isNull();
        assertThat(result.getHighestCaloriesDay()).isNull();
        assertThat(result.getHighestFatDay()).isNull();
        assertThat(result.getHighestCarbsDay()).isNull();
        assertThat(result.getHighestProteinDay()).isNull();
        assertThat(result.getHighestFibreDay()).isNull();
        assertThat(result.getHighestSugarDay()).isNull();
        assertThat(result.getMostRecentMissingDay()).isNull();
        assertThat(result.getLastEntryDate()).isNull();
        assertThat(result.getMissingDaysCount()).isZero();
        assertThat(result.getCurrentStreak()).isZero();
        assertThat(result.getLongestStreak()).isZero();
    }

    /**
     * With MongoDB disabled the repository bean does not exist at all, and the service has to say so
     * rather than fail on a null reference. {@code IllegalStateException} is what
     * {@code GlobalExceptionHandler} maps to 503.
     */
    @Test
    void getStats_whenMongoDbIsDisabled_shouldSayItIsNotConfigured() {
        StatsService withoutMongo = new StatsService(providerOf(null));

        assertThatThrownBy(withoutMongo::getStats)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MongoDB is not configured");
    }

    @Test
    void getCoverageWindow_shouldReportTheEdgesWithoutAggregatingAnything() {
        // given
        when(statsRepository.countAll()).thenReturn(357L);
        when(statsRepository.findFirstEntryDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(statsRepository.findLastEntryDate()).thenReturn(LocalDate.of(2024, 12, 22));

        // when
        CoverageWindowDTO result = statsService().getCoverageWindow();

        // then
        assertThat(result.getEntryCount()).isEqualTo(357L);
        assertThat(result.getFirstEntryDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.getLastEntryDate()).isEqualTo(LocalDate.of(2024, 12, 22));
        // this is what makes it the cheap alternative to getStats(): a count and two indexed reads
        verify(statsRepository, never()).averages(any(), any());
        verify(statsRepository, never()).countUniqueProducts();
        verify(statsRepository, never()).findHighestDayPerMetric();
    }

    @Test
    void getCoverageWindow_shouldReportAnEmptyDiaryWithoutLookingForEdges() {
        // given
        when(statsRepository.countAll()).thenReturn(0L);

        // when
        CoverageWindowDTO result = statsService().getCoverageWindow();

        // then a fresh install answers rather than failing, as getStats() does
        assertThat(result.getEntryCount()).isZero();
        assertThat(result.getFirstEntryDate()).isNull();
        assertThat(result.getLastEntryDate()).isNull();
        verify(statsRepository, never()).findFirstEntryDate();
    }

    @Test
    void getExtremeDays_shouldReturnRoundedDaysInOrder() {
        // given
        when(statsRepository.findExtremeDays(NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 2,
                LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31)))
                .thenReturn(List.of(
                        StatsDTO.DayStats.builder().date(LocalDate.of(2024, 3, 1)).total(3200.44).build(),
                        StatsDTO.DayStats.builder().date(LocalDate.of(2024, 3, 5)).total(3100.06).build()));

        // when
        List<StatsDTO.DayStats> result = statsService().getExtremeDays(
                NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 2, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTotal()).isEqualTo(3200.4);
        assertThat(result.get(1).getTotal()).isEqualTo(3100.1);
    }

    @Test
    void getExtremeDays_whenFromIsAfterTo_shouldThrowException() {
        StatsService service = statsService();
        assertThatThrownBy(() -> service.getExtremeDays(NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 5,
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1)))
                .isInstanceOf(DateTimeException.class)
                .hasMessage("The 'from' date cannot be after the 'to' date");
    }

    @Test
    void getTrend_whenBucketedByWeek_shouldGroupIsoWeeksAndAverageOnlyLoggedDays() {
        // given - 2024-01-01 is a Monday, so the first three days share ISO week 2024-W01
        when(statsRepository.findDailyTotals(any(), any()))
                .thenReturn(List.of(
                        entry(LocalDate.of(2024, 1, 1), 2000),
                        entry(LocalDate.of(2024, 1, 2), 3000),
                        entry(LocalDate.of(2024, 1, 3), 2500),
                        entry(LocalDate.of(2024, 1, 8), 1000)));

        // when
        List<TrendPointDTO> result = statsService().getTrend(NutrientMetric.CALORIES,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 14), TrendGranularity.WEEK);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getBucket()).isEqualTo("2024-W01");
        assertThat(result.getFirst().getDayCount()).isEqualTo(3);
        assertThat(result.getFirst().getTotal()).isEqualTo(7500.0);
        assertThat(result.getFirst().getAverage()).isEqualTo(2500.0);
        assertThat(result.getFirst().getFromDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.getFirst().getToDate()).isEqualTo(LocalDate.of(2024, 1, 3));
        assertThat(result.get(1).getBucket()).isEqualTo("2024-W02");
        assertThat(result.get(1).getDayCount()).isEqualTo(1);
    }

    @Test
    void getTrend_whenBucketedByMonth_shouldUseYearMonthLabels() {
        // given
        when(statsRepository.findDailyTotals(any(), any()))
                .thenReturn(List.of(
                        entry(LocalDate.of(2024, 1, 31), 2000),
                        entry(LocalDate.of(2024, 2, 1), 1000)));

        // when
        List<TrendPointDTO> result = statsService().getTrend(NutrientMetric.CALORIES,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 29), TrendGranularity.MONTH);

        // then
        assertThat(result).extracting(TrendPointDTO::getBucket).containsExactly("2024-01", "2024-02");
    }

    @Test
    void getWeekdayBreakdown_shouldGroupByDayOfWeekStartingWithMonday() {
        // given - 2024-01-01 and 2024-01-08 are Mondays, 2024-01-06 is a Saturday
        when(statsRepository.findDailyTotals(null, null))
                .thenReturn(List.of(
                        entry(LocalDate.of(2024, 1, 6), 4000),
                        entry(LocalDate.of(2024, 1, 1), 2000),
                        entry(LocalDate.of(2024, 1, 8), 3000)));

        // when
        List<WeekdayStatsDTO> result = statsService().getWeekdayBreakdown(null, null);

        // then
        assertThat(result).extracting(WeekdayStatsDTO::getDayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
        assertThat(result.get(0).getDayCount()).isEqualTo(2);
        assertThat(result.get(0).getAverages().getAvgTotalCalories()).isEqualTo(2500.0);
        assertThat(result.get(1).getDayCount()).isEqualTo(1);
        assertThat(result.get(1).getAverages().getAvgTotalCalories()).isEqualTo(4000.0);
    }

    @Test
    void getMacroSplit_shouldWeightMacrosByCaloriesNotByGrams() {
        // given - 100g fat = 900 kcal, 200g carbs = 800 kcal, 50g protein = 200 kcal
        when(statsRepository.averages(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(Optional.of(StatsDTO.Averages.builder()
                        .avgTotalCalories(1950)
                        .avgTotalFat(100)
                        .avgTotalCarbs(200)
                        .avgTotalProtein(50)
                        .build()));

        // when
        MacroSplitDTO result = statsService().getMacroSplit(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        // then
        assertThat(result.getFatCalories()).isEqualTo(900.0);
        assertThat(result.getCarbsCalories()).isEqualTo(800.0);
        assertThat(result.getProteinCalories()).isEqualTo(200.0);
        assertThat(result.getMacroCalories()).isEqualTo(1900.0);
        assertThat(result.getAverageCalories()).isEqualTo(1950.0);
        // gram-weighted would have been 28.6 / 57.1 / 14.3
        assertThat(result.getFatPercentage()).isEqualTo(47.4);
        assertThat(result.getCarbsPercentage()).isEqualTo(42.1);
        assertThat(result.getProteinPercentage()).isEqualTo(10.5);
    }

    /**
     * An empty range is the caller's choice of range, not a broken server - 400, not a 5xx.
     */
    @Test
    void getAveragesForDateRange_whenNothingLoggedInRange_shouldRejectTheRange() {
        when(statsRepository.averages(any(), any())).thenReturn(Optional.empty());
        StatsService service = statsService();

        assertThatThrownBy(() -> service.getAveragesForDateRange(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nothing to average");
    }

    @Test
    void getMissingDays_shouldReportDaysWithoutAnEntry() {
        // given - only the 2nd and the 4th are logged
        when(statsRepository.findLoggedDates(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5)))
                .thenReturn(Set.of(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4)));

        // when
        List<LocalDate> result = statsService().getMissingDays(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5));

        // then
        assertThat(result).containsExactly(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 5));
    }

    @Test
    void getMissingDays_whenEveryDayLogged_shouldReturnEmptyList() {
        // given
        when(statsRepository.findLoggedDates(any(), any()))
                .thenReturn(Set.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)));

        // when
        List<LocalDate> result = statsService().getMissingDays(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getMissingDays_whenFromIsAfterTo_shouldThrowException() {
        StatsService service = statsService();
        assertThatThrownBy(() -> service.getMissingDays(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1)))
                .isInstanceOf(DateTimeException.class);
    }

    @Test
    void countByDateRange_shouldDelegateToTheRepository() {
        when(statsRepository.countInRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))).thenReturn(28L);

        assertThat(statsService().countByDateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .isEqualTo(28L);
    }

    /**
     * The six all-time extremes come back from a single aggregation now, so a test that does not care
     * which metric is which can hand the same day back for all of them.
     */
    private Map<NutrientMetric, StatsDTO.DayStats> highestDayForEveryMetric(StatsDTO.DayStats dayStats) {
        Map<NutrientMetric, StatsDTO.DayStats> byMetric = new EnumMap<>(NutrientMetric.class);
        for (NutrientMetric metric : NutrientMetric.values()) {
            byMetric.put(metric, dayStats);
        }
        return byMetric;
    }

    private FddbData entry(LocalDate date, double calories) {
        FddbData data = new FddbData();
        data.setDate(date);
        data.setTotalCalories(calories);
        return data;
    }

}
