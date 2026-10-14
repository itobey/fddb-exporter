package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import dev.itobey.adapter.api.fddb.exporter.domain.RollingAveragePreset;
import dev.itobey.adapter.api.fddb.exporter.domain.UserSettings;
import dev.itobey.adapter.api.fddb.exporter.service.UserSettingsService;
import dev.itobey.adapter.api.fddb.exporter.ui.service.MigrationClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SettingsViewTest {

    @Test
    void undoDeletion_shouldRestoreTheRangeAndPreserveSubsequentSettingsChanges() {
        UserSettings initial = new UserSettings();
        initial.setRollingAveragePresets(new ArrayList<>(List.of(preset("First"), preset("Deleted"), preset("Last"))));
        AtomicReference<UserSettings> stored = new AtomicReference<>(initial);
        UserSettingsService service = mock(UserSettingsService.class);
        when(service.getSettings()).thenAnswer(invocation -> copy(stored.get()));
        doAnswer(invocation -> {
            stored.set(copy(invocation.getArgument(0)));
            return null;
        }).when(service).saveSettings(any(UserSettings.class));

        SettingsView view = new SettingsView(mock(MigrationClient.class), service);
        button(view, "Delete preset Deleted").click();
        assertThat(stored.get().getRollingAveragePresets()).extracting(RollingAveragePreset::getName)
                .containsExactly("First", "Last");

        // Another tab changes settings while this tab still offers Undo.
        stored.get().getRollingAveragePresets().add(preset("New in another tab"));
        stored.get().setCorrelationInclusionKeywords(new ArrayList<>(List.of("oats")));
        button(view, "Undo deletion of preset Deleted").click();

        assertThat(stored.get().getRollingAveragePresets()).extracting(RollingAveragePreset::getName)
                .containsExactly("First", "Deleted", "Last", "New in another tab");
        assertThat(stored.get().getRollingAveragePresets().get(1).getFromDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(stored.get().getCorrelationInclusionKeywords()).containsExactly("oats");
        assertThat(components(view).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getAriaLabel().orElse("").equals("Undo deletion of preset Deleted")))
                .isEmpty();
    }

    private static Button button(Component root, String label) {
        return components(root).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getAriaLabel().orElse("").equals(label)).findFirst().orElseThrow();
    }

    private static Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(SettingsViewTest::components));
    }

    private static RollingAveragePreset preset(String name) {
        RollingAveragePreset preset = new RollingAveragePreset();
        preset.setName(name);
        preset.setFromDate(LocalDate.of(2026, 10, 1));
        preset.setToDate(LocalDate.of(2026, 10, 14));
        return preset;
    }

    private static UserSettings copy(UserSettings source) {
        UserSettings copy = new UserSettings();
        copy.setRollingAveragePresets(new ArrayList<>(source.getRollingAveragePresets()));
        copy.setCorrelationInclusionKeywords(new ArrayList<>(source.getCorrelationInclusionKeywords()));
        return copy;
    }
}
