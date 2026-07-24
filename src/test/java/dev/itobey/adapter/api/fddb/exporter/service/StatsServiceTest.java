package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private StatsService statsService;

    @Test
    void getStats_shouldGatherAllData() {
        // given
        when(mongoTemplate.count(any(Query.class), eq(StatsService.COLLECTION_NAME))).thenReturn(100L);

        FddbData mockData = new FddbData();
        mockData.setDate(LocalDate.of(2023, 1, 1));
        when(mongoTemplate.findOne(any(Query.class), eq(FddbData.class), eq(StatsService.COLLECTION_NAME))).thenReturn(mockData);

        StatsDTO.Averages mockAverages = StatsDTO.Averages.builder()
                .avgTotalCalories(2000)
                .avgTotalFat(70)
                .avgTotalCarbs(250)
                .avgTotalSugar(50)
                .avgTotalProtein(100)
                .avgTotalFibre(30)
                .build();

        Document rawResults = new Document();
        AggregationResults<StatsDTO.Averages> mockResults = new AggregationResults<>(Collections.singletonList(mockAverages), rawResults);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(StatsService.COLLECTION_NAME), eq(StatsDTO.Averages.class)))
                .thenReturn(mockResults);

        StatsDTO.DayStats mockDayStats = StatsDTO.DayStats.builder()
                .date(LocalDate.of(2023, 5, 1))
                .total(3000)
                .build();

        AggregationResults<StatsDTO.DayStats> mockDayStatsResults = new AggregationResults<>(Collections.singletonList(mockDayStats), rawResults);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(StatsService.COLLECTION_NAME), eq(StatsDTO.DayStats.class)))
                .thenReturn(mockDayStatsResults);

        AggregationResults<Document> mockUniqueProductsResults = new AggregationResults<>(
                Collections.singletonList(new Document("uniqueCount", 42L)), rawResults);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(StatsService.COLLECTION_NAME), eq(Document.class)))
                .thenReturn(mockUniqueProductsResults);

        // when
        StatsDTO result = statsService.getStats();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmountEntries()).isEqualTo(100L);
        assertThat(result.getFirstEntryDate()).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(result.getEntryPercentage()).isGreaterThan(0);
        assertThat(result.getUniqueProducts()).isEqualTo(42L);
        assertThat(result.getAverageTotals()).isEqualTo(mockAverages);
        assertThat(result.getHighestCaloriesDay()).isEqualTo(mockDayStats);
        assertThat(result.getHighestFatDay()).isEqualTo(mockDayStats);
        assertThat(result.getHighestCarbsDay()).isEqualTo(mockDayStats);
        assertThat(result.getHighestProteinDay()).isEqualTo(mockDayStats);
        assertThat(result.getHighestFibreDay()).isEqualTo(mockDayStats);
        assertThat(result.getHighestSugarDay()).isEqualTo(mockDayStats);
        assertThat(result.getMostRecentMissingDay()).isNotNull();
        assertThat(result.getLastEntryDate()).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(result.getMissingDaysCount()).isPositive();
        // nothing is logged in this setup, so there is no streak to report
        assertThat(result.getCurrentStreak()).isZero();
        assertThat(result.getLongestStreak()).isZero();
    }

    @Test
    void getStats_shouldDeriveMissingDayCountAndStreaksFromOneQuery() {
        // given
        LocalDate today = LocalDate.now();
        LocalDate firstEntryDate = today.minusDays(4);
        when(mongoTemplate.count(any(Query.class), eq(StatsService.COLLECTION_NAME))).thenReturn(3L);

        FddbData mockData = new FddbData();
        mockData.setDate(firstEntryDate);
        when(mongoTemplate.findOne(any(Query.class), eq(FddbData.class), eq(StatsService.COLLECTION_NAME))).thenReturn(mockData);

        // logged: -4, -3, -1 - so -2 is a gap and today is not logged yet
        when(mongoTemplate.find(any(Query.class), eq(FddbData.class), eq(StatsService.COLLECTION_NAME)))
                .thenReturn(List.of(
                        entry(today.minusDays(4), 2000),
                        entry(today.minusDays(3), 2000),
                        entry(today.minusDays(1), 2000)));

        Document rawResults = new Document();
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(StatsService.COLLECTION_NAME), eq(StatsDTO.Averages.class)))
                .thenReturn(new AggregationResults<>(Collections.singletonList(StatsDTO.Averages.builder().build()), rawResults));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(StatsService.COLLECTION_NAME), eq(StatsDTO.DayStats.class)))
                .thenReturn(new AggregationResults<>(Collections.emptyList(), rawResults));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(StatsService.COLLECTION_NAME), eq(Document.class)))
                .thenReturn(new AggregationResults<>(Collections.singletonList(new Document("uniqueCount", 3L)), rawResults));

        // when
        StatsDTO result = statsService.getStats();

        // then
        assertThat(result.getMostRecentMissingDay()).isEqualTo(today.minusDays(2));
        assertThat(result.getMissingDaysCount()).isEqualTo(1L);
        // yesterday is logged and today being unlogged does not break the streak yet
        assertThat(result.getCurrentStreak()).isEqualTo(1);
        assertThat(result.getLongestStreak()).isEqualTo(2);
    }

    @Test
    void getStats_shouldReturnZerosAndNullsForEmptyDatabase() {
        // given
        when(mongoTemplate.count(any(Query.class), eq(StatsService.COLLECTION_NAME))).thenReturn(0L);

        // when
        StatsDTO result = statsService.getStats();

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

    @Test
    void getExtremeDays_shouldReturnRoundedDaysInOrder() {
        // given
        List<StatsDTO.DayStats> aggregated = List.of(
                StatsDTO.DayStats.builder().date(LocalDate.of(2024, 3, 1)).total(3200.44).build(),
                StatsDTO.DayStats.builder().date(LocalDate.of(2024, 3, 5)).total(3100.06).build());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(StatsService.COLLECTION_NAME), eq(StatsDTO.DayStats.class)))
                .thenReturn(new AggregationResults<>(aggregated, new Document()));

        // when
        List<StatsDTO.DayStats> result = statsService.getExtremeDays(
                NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 2, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTotal()).isEqualTo(3200.4);
        assertThat(result.get(1).getTotal()).isEqualTo(3100.1);
    }

    @Test
    void getExtremeDays_whenFromIsAfterTo_shouldThrowException() {
        assertThatThrownBy(() -> statsService.getExtremeDays(NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 5,
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The 'from' date cannot be after the 'to' date");
    }

    @Test
    void getTrend_whenBucketedByWeek_shouldGroupIsoWeeksAndAverageOnlyLoggedDays() {
        // given - 2024-01-01 is a Monday, so the first three days share ISO week 2024-W01
        when(mongoTemplate.find(any(Query.class), eq(FddbData.class), eq(StatsService.COLLECTION_NAME)))
                .thenReturn(List.of(
                        entry(LocalDate.of(2024, 1, 1), 2000),
                        entry(LocalDate.of(2024, 1, 2), 3000),
                        entry(LocalDate.of(2024, 1, 3), 2500),
                        entry(LocalDate.of(2024, 1, 8), 1000)));

        // when
        List<TrendPointDTO> result = statsService.getTrend(NutrientMetric.CALORIES,
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
        when(mongoTemplate.find(any(Query.class), eq(FddbData.class), eq(StatsService.COLLECTION_NAME)))
                .thenReturn(List.of(
                        entry(LocalDate.of(2024, 1, 31), 2000),
                        entry(LocalDate.of(2024, 2, 1), 1000)));

        // when
        List<TrendPointDTO> result = statsService.getTrend(NutrientMetric.CALORIES,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 29), TrendGranularity.MONTH);

        // then
        assertThat(result).extracting(TrendPointDTO::getBucket).containsExactly("2024-01", "2024-02");
    }

    @Test
    void getWeekdayBreakdown_shouldGroupByDayOfWeekStartingWithMonday() {
        // given - 2024-01-01 and 2024-01-08 are Mondays, 2024-01-06 is a Saturday
        when(mongoTemplate.find(any(Query.class), eq(FddbData.class), eq(StatsService.COLLECTION_NAME)))
                .thenReturn(List.of(
                        entry(LocalDate.of(2024, 1, 6), 4000),
                        entry(LocalDate.of(2024, 1, 1), 2000),
                        entry(LocalDate.of(2024, 1, 8), 3000)));

        // when
        List<WeekdayStatsDTO> result = statsService.getWeekdayBreakdown(null, null);

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
        StatsDTO.Averages averages = StatsDTO.Averages.builder()
                .avgTotalCalories(1950)
                .avgTotalFat(100)
                .avgTotalCarbs(200)
                .avgTotalProtein(50)
                .build();
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(StatsService.COLLECTION_NAME), eq(StatsDTO.Averages.class)))
                .thenReturn(new AggregationResults<>(List.of(averages), new Document()));

        // when
        MacroSplitDTO result = statsService.getMacroSplit(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

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

    @Test
    void getMissingDays_shouldReportDaysWithoutAnEntry() {
        // given - only the 2nd and the 4th are logged
        when(mongoTemplate.find(any(Query.class), eq(FddbData.class), eq(StatsService.COLLECTION_NAME)))
                .thenReturn(List.of(
                        entry(LocalDate.of(2024, 1, 2), 2000),
                        entry(LocalDate.of(2024, 1, 4), 2000)));

        // when
        List<LocalDate> result = statsService.getMissingDays(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5));

        // then
        assertThat(result).containsExactly(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 5));
    }

    @Test
    void getMissingDays_whenEveryDayLogged_shouldReturnEmptyList() {
        // given
        when(mongoTemplate.find(any(Query.class), eq(FddbData.class), eq(StatsService.COLLECTION_NAME)))
                .thenReturn(List.of(
                        entry(LocalDate.of(2024, 1, 1), 2000),
                        entry(LocalDate.of(2024, 1, 2), 2000)));

        // when
        List<LocalDate> result = statsService.getMissingDays(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getMissingDays_whenFromIsAfterTo_shouldThrowException() {
        assertThatThrownBy(() -> statsService.getMissingDays(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private FddbData entry(LocalDate date, double calories) {
        FddbData data = new FddbData();
        data.setDate(date);
        data.setTotalCalories(calories);
        return data;
    }

}
