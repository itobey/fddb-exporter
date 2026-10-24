package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportProgressDTO;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.FddbDataClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataExportViewTest {
    @Test
    void rangeSyncShowsEachCompletedDayBeforeTheRangeFinishes() throws Exception {
        checkIncrementalSync(false, 1);
    }

    @Test
    void interruptedRangeKeepsCompletedChipsAndEnablesRetry() throws Exception {
        checkIncrementalSync(true, 1);
    }

    @Test
    void longRangeKeepsTheNewestCompletedChipVisible() throws Exception {
        checkIncrementalSync(false, 11);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false, false", "true, false", "true, true"})
    void recentDaysShowsProgressAndKeepsIncludeToday(boolean includeToday, boolean interrupt) throws Exception {
        checkIncrementalSync(interrupt, 1, "Sync Recent Days", includeToday);
    }

    @Test
    void yesterdayUsesTheSameProgressFlow() throws Exception {
        checkIncrementalSync(false, 1, "Sync Yesterday", false);
    }

    private void checkIncrementalSync(boolean interrupt, int completedDays) throws Exception {
        checkIncrementalSync(interrupt, completedDays, "Sync Date Range", false);
    }

    private void checkIncrementalSync(boolean interrupt, int completedDays, String label, boolean includeToday) throws Exception {
        FddbDataClient client = mock(FddbDataClient.class);
        CountDownLatch firstDay = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch workerFinished = new CountDownLatch(1);
        org.mockito.stubbing.Answer<Void> answer = call -> {
            Consumer<ExportProgressDTO> progress = call.getArgument(label.equals("Sync Date Range") ? 1 : 2);
            for (int day = 1; day <= completedDays; day++) {
                progress.accept(new ExportProgressDTO(java.time.LocalDate.of(2026, 10, day).toString(), true, false, null));
            }
            firstDay.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
                if (interrupt) {
                    throw new ApiException("Connection interrupted");
                }
                progress.accept(new ExportProgressDTO(java.time.LocalDate.of(2026, 10, completedDays + 1).toString(), false, false, null));
                progress.accept(new ExportProgressDTO(null, false, true, null));
                return null;
            } finally {
                workerFinished.countDown();
            }
        };
        if (label.equals("Sync Date Range")) {
            doAnswer(answer).when(client).exportWithProgress(any(), any());
        } else {
            doAnswer(answer).when(client).exportRecentWithProgress(anyInt(), anyBoolean(), any());
        }
        UI ui = new UI();
        UI.setCurrent(ui);
        try {
            DataExportView view = new DataExportView(client);
            ui.add(view);
            descendants(view).filter(com.vaadin.flow.component.checkbox.Checkbox.class::isInstance)
                    .map(com.vaadin.flow.component.checkbox.Checkbox.class::cast)
                    .forEach(checkbox -> checkbox.setValue(includeToday));
            Button sync = descendants(view).filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getText().equals(label)).findFirst().orElseThrow();
            sync.click();
            assertThat(firstDay.await(2, TimeUnit.SECONDS)).isTrue();
            view.checkSyncProgress();
            assertThat(chips(view)).hasSize(completedDays);
            assertThat(sync.isEnabled()).isFalse();
            assertThat(chips(view).filter(Component::isVisible)).hasSize(Math.min(10, completedDays));
            assertThat(chips(view).toList().getLast().isVisible()).isTrue();
            release.countDown();
            assertThat(workerFinished.await(2, TimeUnit.SECONDS)).isTrue();
            // Completion is published immediately after the mocked client returns/throws.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!sync.isEnabled() && System.nanoTime() < deadline) {
                view.checkSyncProgress();
                Thread.sleep(5);
            }
            assertThat(sync.isEnabled()).isTrue();
            if (!label.equals("Sync Date Range")) {
                verify(client).exportRecentWithProgress(eq(label.equals("Sync Yesterday") ? 1 : 7), eq(includeToday), any());
                verify(client, never()).exportForDaysBack(anyInt(), anyBoolean());
                assertThat(chips(view).findFirst().orElseThrow().getParent().orElseThrow()
                        .getParent().orElseThrow().getParent().orElseThrow().getChildren()
                        .filter(Button.class::isInstance).map(Button.class::cast).map(Button::getText)).contains(label);
            }
            if (interrupt) {
                assertThat(chips(view).map(chip -> chip.getElement().getAttribute("title")))
                        .containsExactly("2026-10-01");
                assertThat(descendants(view).filter(Span.class::isInstance).map(Span.class::cast).map(Span::getText))
                        .contains("Connection interrupted");
            } else {
                assertThat(chips(view)).hasSize(completedDays + 1);
                assertThat(chips(view).filter(chip -> chip.hasClassName("export-chip--failed"))).hasSize(1);
            }
        } finally {
            release.countDown();
            ui.removeAll();
            UI.setCurrent(null);
        }
    }

    private static Stream<Span> chips(Component view) {
        return descendants(view).filter(Span.class::isInstance).map(Span.class::cast)
                .filter(span -> span.hasClassName("export-chip"));
    }

    private static Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(DataExportViewTest::descendants));
    }
}
