package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.tabs.TabSheet;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import dev.itobey.adapter.api.fddb.exporter.ui.service.FddbDataClient;
import dev.itobey.adapter.api.fddb.exporter.ui.service.StatsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DateRangeInsightsViewsTest {
    private final StatsClient client = mock(StatsClient.class);
    private final FddbExporterProperties properties = mock(FddbExporterProperties.class, RETURNS_DEEP_STUBS);
    private final LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));

    @BeforeEach
    void setUp() {
        UI.setCurrent(new UI());
        when(properties.getPersistence().getMongodb().isEnabled()).thenReturn(true);
        doReturn(ZoneId.of("Europe/Berlin")).when(properties).resolveZone();
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    void trendClampsTheRequestAndShowsElapsedCoverage() throws Exception {
        TrendView view = new TrendView(client, properties);
        LocalDate from = today.minusDays(6);
        when(client.getTrend(NutrientMetric.CALORIES, from.toString(), today.toString(), TrendGranularity.WEEK))
                .thenReturn(List.of(TrendPointDTO.builder().bucket("test").fromDate(today).toDate(today)
                        .dayCount(1).total(2000).average(2000).build()));
        setDates(view, from, today.plusDays(7));

        button(view, "Show Trend").click();

        verify(client).getTrend(NutrientMetric.CALORIES, from.toString(), today.toString(), TrendGranularity.WEEK);
        assertThat(view.getElement().getTextRecursively())
                .contains("1 of 7 days logged through today.", "Future dates are excluded.", "per logged day");
        assertThat(date(view, "To Date").getValue()).isEqualTo(today.plusDays(7));
    }

    @Test
    void futureTrendRangeClearsPreviousResultsWithoutRequestingFutureData() throws Exception {
        TrendView view = new TrendView(client, properties);
        LocalDate from = today.minusDays(2);
        when(client.getTrend(NutrientMetric.CALORIES, from.toString(), today.toString(), TrendGranularity.WEEK))
                .thenReturn(List.of());
        setDates(view, from, today);
        button(view, "Show Trend").click();
        clearInvocations(client);
        setDates(view, today.plusDays(1), today.plusDays(7));

        button(view, "Show Trend").click();

        verifyNoInteractions(client);
        assertThat(view.getElement().getTextRecursively())
                .contains("This range is in the future. No logged data yet.")
                .doesNotContain("No entries found from");
    }

    @Test
    void missingDaysChecksOnlyElapsedDatesAndExplainsTheLimit() throws Exception {
        Component tab = missingTab();
        LocalDate from = today.minusDays(2);
        when(client.getMissingDays(from.toString(), today.toString())).thenReturn(List.of(from));
        setDates(tab, from, today.plusDays(10));

        button(tab, "Find Gaps").click();

        verify(client).getMissingDays(from.toString(), today.toString());
        assertThat(tab.getElement().getTextRecursively()).contains("1 missing days", "Future dates are excluded");
        assertThat(date(tab, "To Date").getValue()).isEqualTo(today.plusDays(10));
    }

    @Test
    void entirelyFutureMissingRangeDoesNotClaimTheDiaryHasGapsOrIsComplete() {
        Component tab = missingTab();
        setDates(tab, today.plusDays(1), today.plusDays(7));

        button(tab, "Find Gaps").click();

        verifyNoInteractions(client);
        assertThat(tab.getElement().getTextRecursively())
                .contains("This range is in the future. No logged data yet.")
                .doesNotContain("every day in this range is logged");
    }

    private Component missingTab() {
        EntriesView view = new EntriesView(mock(FddbDataClient.class), client, properties);
        TabSheet tabs = descendants(view).filter(TabSheet.class::isInstance).map(TabSheet.class::cast)
                .findFirst().orElseThrow();
        tabs.setSelectedIndex(2);
        return tabs.getComponent(tabs.getTabAt(2));
    }

    private static void setDates(Component root, LocalDate from, LocalDate to) {
        date(root, "From Date").setValue(from);
        date(root, "To Date").setValue(to);
    }

    private static DatePicker date(Component root, String label) {
        return descendants(root).filter(DatePicker.class::isInstance).map(DatePicker.class::cast)
                .filter(field -> field.getLabel().equals(label)).findFirst().orElseThrow();
    }

    private static Button button(Component root, String label) {
        return descendants(root).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals(label)).findFirst().orElseThrow();
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(DateRangeInsightsViewsTest::descendants));
    }
}

