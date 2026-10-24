package dev.itobey.adapter.api.fddb.exporter.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.select.Select;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ChangelogClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChangelogDialogTest {
    private UI ui;

    @BeforeEach
    void setUp() {
        ui = new UI();
        UI.setCurrent(ui);
    }

    @AfterEach
    void tearDown() { UI.setCurrent(null); }

    @Test
    void rendersEntireHistoryAndMakesOlderReleasesAvailableInSelector() {
        var client = mock(ChangelogClient.class);
        var releases = List.of(new ChangelogClient.Release("2.6.2", "### Fixed\n- New fix"),
                new ChangelogClient.Release("1.0.0", "- First release"));
        when(client.load()).thenReturn(CompletableFuture.completedFuture(
                new ChangelogClient.Snapshot(releases, Instant.EPOCH, false)));
        var dialog = new ChangelogDialog(client, "1.0.0");
        dialog.open();
        assertThat(descendants(dialog).filter(Markdown.class::isInstance).map(Markdown.class::cast)
                .map(Markdown::getContent)).containsExactly("### Fixed\n- New fix", "- First release");
        Select<?> selector = descendants(dialog).filter(Select.class::isInstance).map(Select.class::cast).findFirst().orElseThrow();
        assertThat(selector.isEnabled()).isTrue();
        assertThat(selector.getListDataView().getItemCount()).isEqualTo(2);
        assertThat(descendants(dialog).map(c -> c.getElement().getText())).contains("Installed");
    }

    @Test
    void lateResponseAfterClosingIsIgnoredAndFailureCanBeRetried() {
        var client = mock(ChangelogClient.class);
        CompletableFuture<ChangelogClient.Snapshot> pending = new CompletableFuture<>();
        when(client.load()).thenReturn(pending);
        var dialog = new ChangelogDialog(client, "dev");
        dialog.open();
        dialog.close();
        pending.completeExceptionally(new IllegalStateException("Offline"));
        dialog.checkLoad(1);
        assertThat(descendants(dialog).filter(Markdown.class::isInstance)).isEmpty();
        UI.setCurrent(ui);
        dialog.open();
        assertThat(descendants(dialog).map(c -> c.getElement().getText()))
                .anyMatch(text -> text.contains("Couldn’t load the changelog"));
        var snapshot = new ChangelogClient.Snapshot(List.of(new ChangelogClient.Release("1.0.0", "- First release")), Instant.EPOCH, false);
        when(client.load()).thenReturn(CompletableFuture.completedFuture(snapshot));
        descendants(dialog).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals("Retry")).findFirst().orElseThrow().click();
        assertThat(descendants(dialog).filter(Markdown.class::isInstance)).hasSize(1);
    }

    private Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::descendants));
    }
}
