package dev.itobey.adapter.api.fddb.exporter.ui.components;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.html.NativeButton;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TrendChartTest {
    @Test
    void currentWeekCoverageExcludesFutureDaysInDetailsAndAccessibleLabels() {
        TrendPointDTO point = point("2026-10-26", 3);
        LocalDate from = LocalDate.parse("2026-10-01");
        LocalDate today = LocalDate.parse("2026-10-28");
        TrendChart chart = new TrendChart(List.of(point), TrendGranularity.WEEK, "kcal", "red", from, today);

        assertThat(TrendChart.coverage(point, TrendGranularity.WEEK, from, today)).isEqualTo("3 of 3 days logged");
        assertThat(TrendChart.isIncomplete(point, TrendGranularity.WEEK, from, today)).isFalse();
        assertThat(chart.getElement().getTextRecursively()).contains("3 of 3 days logged");
        NativeButton bar = components(chart).filter(NativeButton.class::isInstance)
                .map(NativeButton.class::cast).findFirst().orElseThrow();
        assertThat(bar.getElement().getAttribute("aria-label")).contains("3 of 3 days logged");
        assertThat(bar.hasClassName("trend-period--incomplete")).isFalse();
    }

    @Test
    void coverageClipsPartialMonthsButStillCountsMissingDaysAtBothEdges() {
        TrendPointDTO point = point("2024-02-12", 10);
        LocalDate from = LocalDate.parse("2024-02-10");
        LocalDate to = LocalDate.parse("2024-02-25");
        assertThat(TrendChart.coverage(point, TrendGranularity.MONTH, from, to)).isEqualTo("10 of 16 days logged");
        assertThat(TrendChart.isIncomplete(point, TrendGranularity.MONTH, from, to)).isTrue();
    }

    @Test
    void coverage_shouldDistinguishAnIncompleteWeekFromACompleteOne() {
        TrendPointDTO first = point("2026-10-05", 7);
        TrendPointDTO last = point("2026-10-12", 2);
        assertThat(TrendChart.isIncomplete(first, TrendGranularity.WEEK)).isFalse();
        assertThat(TrendChart.describe(last, TrendGranularity.WEEK, "kcal"))
                .contains("2 of 7 days logged", "Incomplete coverage", "per logged day");
    }

    @Test
    void monthCoverage_shouldUseTheActualCalendarMonthIncludingLeapYears() {
        assertThat(TrendChart.expectedDays(point("2024-02-01", 29), TrendGranularity.MONTH)).isEqualTo(29);
        assertThat(TrendChart.isIncomplete(point("2024-02-01", 29), TrendGranularity.MONTH)).isFalse();
        assertThat(TrendChart.isIncomplete(point("2025-02-01", 27), TrendGranularity.MONTH)).isTrue();
    }

    @Test
    void selectingABar_shouldUpdateDetailsAndKeepExactlyOneKeyboardTabStop() {
        TrendChart chart = new TrendChart(List.of(point("2026-10-05", 7), point("2026-10-12", 2)),
                TrendGranularity.WEEK, "kcal", "var(--nutrient-calories)");
        List<NativeButton> bars = components(chart).filter(NativeButton.class::isInstance)
                .map(NativeButton.class::cast).toList();
        assertThat(bars.getLast().getElement().getAttribute("aria-pressed")).isEqualTo("true");
        ComponentUtil.fireEvent(bars.getFirst(), new ClickEvent<>(bars.getFirst()));
        assertThat(bars.getFirst().getElement().getAttribute("aria-pressed")).isEqualTo("true");
        assertThat(bars.getLast().getElement().getAttribute("aria-pressed")).isEqualTo("false");
        assertThat(bars.stream().filter(b -> "0".equals(b.getElement().getAttribute("tabindex"))))
                .hasSize(1);
        assertThat(chart.getElement().getTextRecursively()).contains("7 of 7 days logged");
    }

    @Test
    void scale_shouldContainThePeakAndHandleZeroAndFractionalValues() {
        assertThat(TrendChart.scaleCeiling(2861.6)).isEqualTo(3000);
        assertThat(TrendChart.scaleCeiling(0)).isPositive();
        assertThat(TrendChart.scaleCeiling(0.07)).isGreaterThanOrEqualTo(0.07);
    }

    private static TrendPointDTO point(String date, long days) {
        LocalDate from = LocalDate.parse(date);
        return TrendPointDTO.builder().bucket(date).fromDate(from).toDate(from.plusDays(days - 1))
                .dayCount(days).average(2100).total(2100 * days).build();
    }

    private static Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(TrendChartTest::components));
    }
}
