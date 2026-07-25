package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.GoalCheckResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.GoalComparator;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.GoalTargetDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.PeriodComparisonDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FddbAnalysisToolsTest {

    private static final LocalDate A_FROM = LocalDate.of(2024, 2, 1);
    private static final LocalDate A_TO = LocalDate.of(2024, 2, 29);
    private static final LocalDate B_FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate B_TO = LocalDate.of(2024, 1, 31);

    @InjectMocks
    private FddbAnalysisTools fddbAnalysisTools;
    @Mock
    private FddbDataService fddbDataService;

    @Test
    void comparePeriods_shouldReportBothAveragesAndTheChangeFromBToA() {
        // given
        when(fddbDataService.findByDateRange(A_FROM, A_TO, false)).thenReturn(days(A_FROM, 20));
        when(fddbDataService.findByDateRange(B_FROM, B_TO, false)).thenReturn(days(B_FROM, 31));
        stubAverages(A_FROM, A_TO, averages(2200, 100));
        stubAverages(B_FROM, B_TO, averages(2000, 80));

        // when
        PeriodComparisonDTO result =
                fddbAnalysisTools.comparePeriods("2024-02-01", "2024-02-29", "2024-01-01", "2024-01-31");

        // then
        assertNull(result.getMessage());
        assertEquals(29, result.getPeriodA().getDaysInRange());
        assertEquals(20, result.getPeriodA().getLoggedDays());
        assertEquals(31, result.getPeriodB().getLoggedDays());

        PeriodComparisonDTO.MetricDelta calories = deltaOf(result, NutrientMetric.CALORIES);
        assertEquals("kcal", calories.getUnit());
        assertEquals(2200, calories.getPeriodA());
        assertEquals(2000, calories.getPeriodB());
        assertEquals(200, calories.getAbsoluteChange());
        assertEquals(10.0, calories.getPercentageChange());

        PeriodComparisonDTO.MetricDelta protein = deltaOf(result, NutrientMetric.PROTEIN);
        assertEquals("g", protein.getUnit());
        assertEquals(20, protein.getAbsoluteChange());
        assertEquals(25.0, protein.getPercentageChange());
    }

    @Test
    void comparePeriods_shouldReportANegativeChangeWhenPeriodAIsLower() {
        // given
        when(fddbDataService.findByDateRange(A_FROM, A_TO, false)).thenReturn(days(A_FROM, 29));
        when(fddbDataService.findByDateRange(B_FROM, B_TO, false)).thenReturn(days(B_FROM, 31));
        stubAverages(A_FROM, A_TO, averages(1800, 80));
        stubAverages(B_FROM, B_TO, averages(2000, 80));

        // when
        PeriodComparisonDTO result =
                fddbAnalysisTools.comparePeriods("2024-02-01", "2024-02-29", "2024-01-01", "2024-01-31");

        // then
        PeriodComparisonDTO.MetricDelta calories = deltaOf(result, NutrientMetric.CALORIES);
        assertEquals(-200, calories.getAbsoluteChange());
        assertEquals(-10.0, calories.getPercentageChange());
    }

    @Test
    void comparePeriods_shouldExplainAnEmptyPeriodInsteadOfFailingToAverageIt() {
        // given
        when(fddbDataService.findByDateRange(A_FROM, A_TO, false)).thenReturn(List.of());
        when(fddbDataService.findByDateRange(B_FROM, B_TO, false)).thenReturn(days(B_FROM, 31));
        stubAverages(B_FROM, B_TO, averages(2000, 80));

        // when
        PeriodComparisonDTO result =
                fddbAnalysisTools.comparePeriods("2024-02-01", "2024-02-29", "2024-01-01", "2024-01-31");

        // then
        assertTrue(result.getMessage().contains("Period A"));
        assertNull(result.getDeltas());
        assertEquals(0, result.getPeriodA().getLoggedDays());
        assertNull(result.getPeriodA().getAverages());
        // the period that does have data is still reported, so the agent is not left empty-handed
        assertNotNull(result.getPeriodB().getAverages());
        verify(fddbDataService, never()).getRollingAverages(argThat(
                range -> range != null && A_FROM.toString().equals(range.getFromDate())));
    }

    @Test
    void comparePeriods_shouldOmitThePercentageWhenTheBaselineIsZero() {
        // given
        when(fddbDataService.findByDateRange(A_FROM, A_TO, false)).thenReturn(days(A_FROM, 29));
        when(fddbDataService.findByDateRange(B_FROM, B_TO, false)).thenReturn(days(B_FROM, 31));
        stubAverages(A_FROM, A_TO, averages(2000, 80));
        stubAverages(B_FROM, B_TO, averages(2000, 0));

        // when
        PeriodComparisonDTO result =
                fddbAnalysisTools.comparePeriods("2024-02-01", "2024-02-29", "2024-01-01", "2024-01-31");

        // then
        PeriodComparisonDTO.MetricDelta protein = deltaOf(result, NutrientMetric.PROTEIN);
        assertEquals(80, protein.getAbsoluteChange());
        assertNull(protein.getPercentageChange());
    }

    @Test
    void comparePeriods_shouldRejectAnUnparseableDate() {
        // when / then
        assertThrows(DateTimeException.class,
                () -> fddbAnalysisTools.comparePeriods("last month", "today", "2024-01-01", "2024-01-31"));
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void checkGoals_shouldCountOnlyDaysThatPassEveryTarget() {
        // given
        LocalDate from = LocalDate.of(2024, 1, 1);
        when(fddbDataService.findByDateRange(from, from.plusDays(3), false)).thenReturn(List.of(
                day(from, 2000, 130),           // passes both
                day(from.plusDays(1), 2400, 130),  // too many calories
                day(from.plusDays(2), 2000, 90),   // too little protein
                day(from.plusDays(3), 1900, 140))); // passes both

        // when
        GoalCheckResultDTO result = fddbAnalysisTools.checkGoals("2024-01-01", "2024-01-04",
                List.of(atMost(NutrientMetric.CALORIES, 2200), atLeast(NutrientMetric.PROTEIN, 120)), null);

        // then
        assertEquals(4, result.getDaysInRange());
        assertEquals(4, result.getDaysEvaluated());
        assertEquals(2, result.getDaysMet());
        assertEquals(50.0, result.getHitRate());
        // per target, both goals were hit on three of the four days
        assertEquals(3, targetOf(result, NutrientMetric.CALORIES).getDaysMet());
        assertEquals(1, targetOf(result, NutrientMetric.CALORIES).getDaysMissed());
        assertEquals(75.0, targetOf(result, NutrientMetric.PROTEIN).getHitRate());
        assertEquals(2075.0, targetOf(result, NutrientMetric.CALORIES).getAverage());
        assertEquals("g", targetOf(result, NutrientMetric.PROTEIN).getUnit());
    }

    @Test
    void checkGoals_shouldOmitTheIndividualDaysUnlessTheyWereAskedFor() {
        // given
        LocalDate from = LocalDate.of(2024, 1, 1);
        when(fddbDataService.findByDateRange(from, from.plusDays(1), false))
                .thenReturn(List.of(day(from, 2000, 130), day(from.plusDays(1), 2400, 100)));

        // when
        GoalCheckResultDTO without = fddbAnalysisTools.checkGoals("2024-01-01", "2024-01-02",
                List.of(atMost(NutrientMetric.CALORIES, 2200)), null);
        GoalCheckResultDTO with = fddbAnalysisTools.checkGoals("2024-01-01", "2024-01-02",
                List.of(atMost(NutrientMetric.CALORIES, 2200)), true);

        // then
        assertNull(without.getDays());
        assertEquals(2, with.getDays().size());
        GoalCheckResultDTO.DayResult missedDay = with.getDays().getLast();
        assertFalse(missedDay.isMet());
        assertEquals(2400, missedDay.getMissed().getFirst().getActual());
        assertEquals(2200, missedDay.getMissed().getFirst().getTarget());
        // a day that met everything carries no noise about what it did not miss
        assertNull(with.getDays().getFirst().getMissed());
    }

    @Test
    void checkGoals_shouldLetAnUnloggedDayBreakTheStreak() {
        // given: 1st and 2nd met, 3rd never logged, 4th and 5th met
        LocalDate from = LocalDate.of(2024, 1, 1);
        when(fddbDataService.findByDateRange(from, from.plusDays(4), false)).thenReturn(List.of(
                day(from, 2000, 130),
                day(from.plusDays(1), 2000, 130),
                day(from.plusDays(3), 2000, 130),
                day(from.plusDays(4), 2000, 130)));

        // when
        GoalCheckResultDTO result = fddbAnalysisTools.checkGoals("2024-01-01", "2024-01-05",
                List.of(atMost(NutrientMetric.CALORIES, 2200)), null);

        // then
        assertEquals(5, result.getDaysInRange());
        assertEquals(4, result.getDaysEvaluated());
        assertEquals(4, result.getDaysMet());
        assertEquals(100.0, result.getHitRate());
        assertEquals(2, result.getLongestStreak());
        assertEquals(2, result.getCurrentStreak());
    }

    @Test
    void checkGoals_shouldReportAStreakBrokenByTheLastDayAsZero() {
        // given
        LocalDate from = LocalDate.of(2024, 1, 1);
        when(fddbDataService.findByDateRange(from, from.plusDays(2), false)).thenReturn(List.of(
                day(from, 2000, 130),
                day(from.plusDays(1), 2000, 130),
                day(from.plusDays(2), 3000, 130)));

        // when
        GoalCheckResultDTO result = fddbAnalysisTools.checkGoals("2024-01-01", "2024-01-03",
                List.of(atMost(NutrientMetric.CALORIES, 2200)), null);

        // then
        assertEquals(2, result.getLongestStreak());
        assertEquals(0, result.getCurrentStreak());
    }

    @Test
    void checkGoals_shouldExplainARangeWithoutASingleEntry() {
        // given
        LocalDate from = LocalDate.of(2024, 1, 1);
        when(fddbDataService.findByDateRange(from, from.plusDays(6), false)).thenReturn(List.of());

        // when
        GoalCheckResultDTO result = fddbAnalysisTools.checkGoals("2024-01-01", "2024-01-07",
                List.of(atMost(NutrientMetric.CALORIES, 2200)), true);

        // then
        assertEquals(0, result.getDaysEvaluated());
        assertNotNull(result.getMessage());
        assertNull(result.getTargets());
        assertNull(result.getDays());
    }

    @Test
    void checkGoals_shouldRejectAnEmptyTargetList() {
        // when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> fddbAnalysisTools.checkGoals("2024-01-01", "2024-01-07", List.of(), null));

        // then
        assertTrue(exception.getMessage().contains("At least one target"));
        verifyNoInteractions(fddbDataService);
    }

    @Test
    void checkGoals_shouldRejectATargetWithoutAComparator() {
        // given
        GoalTargetDTO incomplete = GoalTargetDTO.builder().metric(NutrientMetric.PROTEIN).value(120).build();

        // when / then
        assertThrows(IllegalArgumentException.class,
                () -> fddbAnalysisTools.checkGoals("2024-01-01", "2024-01-07", List.of(incomplete), null));
        verifyNoInteractions(fddbDataService);
    }

    private void stubAverages(LocalDate from, LocalDate to, StatsDTO.Averages averages) {
        when(fddbDataService.getRollingAverages(argThat(range -> range != null
                && from.toString().equals(range.getFromDate())
                && to.toString().equals(range.getToDate()))))
                .thenReturn(RollingAveragesDTO.builder().averages(averages).build());
    }

    private StatsDTO.Averages averages(double calories, double protein) {
        return StatsDTO.Averages.builder()
                .avgTotalCalories(calories)
                .avgTotalProtein(protein)
                .build();
    }

    private PeriodComparisonDTO.MetricDelta deltaOf(PeriodComparisonDTO comparison, NutrientMetric metric) {
        return comparison.getDeltas().stream()
                .filter(delta -> delta.getMetric() == metric)
                .findFirst()
                .orElseThrow();
    }

    private GoalCheckResultDTO.TargetResult targetOf(GoalCheckResultDTO result, NutrientMetric metric) {
        return result.getTargets().stream()
                .filter(target -> target.getMetric() == metric)
                .findFirst()
                .orElseThrow();
    }

    private GoalTargetDTO atMost(NutrientMetric metric, double value) {
        return GoalTargetDTO.builder().metric(metric).comparator(GoalComparator.AT_MOST).value(value).build();
    }

    private GoalTargetDTO atLeast(NutrientMetric metric, double value) {
        return GoalTargetDTO.builder().metric(metric).comparator(GoalComparator.AT_LEAST).value(value).build();
    }

    private List<FddbDataDTO> days(LocalDate from, int amount) {
        return IntStream.range(0, amount)
                .mapToObj(index -> day(from.plusDays(index), 2000, 100))
                .toList();
    }

    private FddbDataDTO day(LocalDate date, double calories, double protein) {
        FddbDataDTO entry = new FddbDataDTO();
        entry.setDate(date);
        entry.setTotalCalories(calories);
        entry.setTotalProtein(protein);
        return entry;
    }
}
