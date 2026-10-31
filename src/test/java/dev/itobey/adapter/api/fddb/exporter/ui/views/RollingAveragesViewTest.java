package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Paragraph;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.WeekdayStatsDTO;
import dev.itobey.adapter.api.fddb.exporter.ui.service.StatsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RollingAveragesViewTest {
    private final StatsClient client = mock(StatsClient.class);
    private final FddbExporterProperties properties = mock(FddbExporterProperties.class, RETURNS_DEEP_STUBS);
    private final LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
    private RollingAveragesView view;

    @BeforeEach
    void setUp() {
        UI.setCurrent(new UI());
        when(properties.getPersistence().getMongodb().isEnabled()).thenReturn(true);
        doReturn(ZoneId.of("Europe/Berlin")).when(properties).resolveZone();
        view = new RollingAveragesView(client, properties, null);
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    void futureEndUsesOnlyElapsedDaysAndPreservesSelectedRange() throws Exception {
        LocalDate from = today.minusDays(9);
        LocalDate to = today.plusDays(20);
        stubData(from, today, 7);

        calculate(from, to);

        assertThat(text()).contains("Averages based on 7 logged days.",
                "7 of 10 days logged through today.", "Future dates are excluded.");
        assertThat(descendants(view).map(component -> component.getElement().getText()).toList())
                .contains(from + " ⮕ " + to);
        verify(client).getRollingAverages(from.toString(), today.toString());
        verify(client).getMacroSplit(from.toString(), today.toString());
        assertThat(dateField("To Date").getValue()).isEqualTo(to);
    }

    @Test
    void historicalGapsAreVisibleWithoutAFutureDateMessage() throws Exception {
        LocalDate from = today.minusDays(10);
        LocalDate to = today.minusDays(1);
        stubData(from, to, 6);

        calculate(from, to);

        assertThat(text()).contains("Averages based on 6 logged days.", "6 of 10 days logged.")
                .doesNotContain("Future dates are excluded.");
    }

    @Test
    void singleLoggedDayUsesSingularLabels() throws Exception {
        LocalDate date = today.minusDays(1);
        stubData(date, date, 1);

        calculate(date, date);

        assertThat(text()).contains("Averages based on 1 logged day.", "1 of 1 day logged.");
    }

    @Test
    void entirelyFutureRangeShowsEmptyStateWithoutRequests() {
        calculate(today.plusDays(1), today.plusDays(10));

        assertThat(text()).contains("This range is in the future. No logged data yet.");
        verifyNoInteractions(client);
    }

    @Test
    void emptyElapsedRangeDoesNotRequestAveragesOrShowOldResults() throws Exception {
        LocalDate from = today.minusDays(3);
        stubData(from, today, 2);
        calculate(from, today);
        when(client.getWeekdayBreakdown(from.toString(), today.toString())).thenReturn(List.of());
        clearInvocations(client);

        calculate(from, today.plusDays(3));

        assertThat(text()).contains("No logged data in this range.", "0 of 4 days logged through today.")
                .doesNotContain("Averages based on 2 logged days.");
        verify(client).getWeekdayBreakdown(from.toString(), today.toString());
        verifyNoMoreInteractions(client);
    }

    private void stubData(LocalDate from, LocalDate to, long count) throws Exception {
        StatsDTO.Averages averages = StatsDTO.Averages.builder().avgTotalCalories(2000).build();
        when(client.getWeekdayBreakdown(from.toString(), to.toString())).thenReturn(List.of(
                WeekdayStatsDTO.builder().dayOfWeek(DayOfWeek.MONDAY).dayCount(count).averages(averages).build()));
        when(client.getRollingAverages(from.toString(), to.toString())).thenReturn(
                RollingAveragesDTO.builder().fromDate(from.toString()).toDate(to.toString()).averages(averages).build());
    }

    private void calculate(LocalDate from, LocalDate to) {
        dateField("From Date").setValue(from);
        dateField("To Date").setValue(to);
        descendants(view).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals("Calculate Averages")).findFirst().orElseThrow().click();
    }

    private DatePicker dateField(String label) {
        return descendants(view).filter(DatePicker.class::isInstance).map(DatePicker.class::cast)
                .filter(field -> field.getLabel().equals(label)).findFirst().orElseThrow();
    }

    private List<String> text() {
        return descendants(view).filter(Paragraph.class::isInstance).map(Paragraph.class::cast)
                .map(Paragraph::getText).toList();
    }

    private static Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(RollingAveragesViewTest::descendants));
    }
}
