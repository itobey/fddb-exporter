package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.TextField;
import dev.itobey.adapter.api.fddb.exporter.domain.RollingAveragePreset;
import dev.itobey.adapter.api.fddb.exporter.domain.UserSettings;
import dev.itobey.adapter.api.fddb.exporter.service.UserSettingsService;
import dev.itobey.adapter.api.fddb.exporter.ui.service.MigrationClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
    private UI ui;

    @BeforeEach
    void setUpUi() {
        ui = new UI();
        UI.setCurrent(ui);
    }

    @AfterEach
    void clearUi() {
        UI.setCurrent(null);
    }

    @Test
    void editPreset_shouldSaveRenamedRangeInPlaceAndPreserveOtherSettings() {
        Fixture fixture = fixture();
        button(fixture.view(), "Edit preset First").click();
        nameField(fixture.view()).setValue("Renamed");
        dateField(fixture.view(), "From Date").setValue(LocalDate.of(2026, 3, 7));
        dateField(fixture.view(), "To Date").setValue(LocalDate.of(2027, 1, 1));
        // A different tab saves an unrelated setting while the editor is open.
        fixture.stored().get().setCorrelationInclusionKeywords(new ArrayList<>(List.of("oats")));
        button(fixture.view(), "Save changes").click();

        assertThat(fixture.stored().get().getRollingAveragePresets()).extracting(RollingAveragePreset::getName)
                .containsExactly("Renamed", "Second");
        RollingAveragePreset updated = fixture.stored().get().getRollingAveragePresets().getFirst();
        assertThat(updated.getFromDate()).isEqualTo(LocalDate.of(2026, 3, 7));
        assertThat(updated.getToDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(fixture.stored().get().getCorrelationInclusionKeywords()).containsExactly("oats");
        assertThat(nameField(fixture.view()).getParent().orElseThrow().getParent().orElseThrow().isVisible()).isFalse();
    }

    @Test
    void editPreset_shouldRejectDuplicateNamesAndReversedDates() {
        Fixture fixture = fixture();
        button(fixture.view(), "Edit preset First").click();
        nameField(fixture.view()).setValue("second");
        button(fixture.view(), "Save changes").click();
        assertThat(nameField(fixture.view()).isInvalid()).isTrue();

        nameField(fixture.view()).setValue("First");
        dateField(fixture.view(), "To Date").setValue(LocalDate.of(2026, 9, 30));
        button(fixture.view(), "Save changes").click();
        assertThat(dateField(fixture.view(), "To Date").isInvalid()).isTrue();
        verify(fixture.service(), never()).saveSettings(any());
        assertThat(fixture.stored().get().getRollingAveragePresets()).extracting(RollingAveragePreset::getName)
                .containsExactly("First", "Second");
    }

    @Test
    void editPreset_shouldKeepDraftOpenWhenSavingFailsOrPresetWasRemoved() {
        Fixture fixture = fixture();
        button(fixture.view(), "Edit preset First").click();
        nameField(fixture.view()).setValue("Renamed");
        doThrow(new IllegalStateException("Database unavailable")).when(fixture.service()).saveSettings(any());
        button(fixture.view(), "Save changes").click();

        assertThat(nameField(fixture.view()).getValue()).isEqualTo("Renamed");
        assertThat(nameField(fixture.view()).getParent().orElseThrow().getParent().orElseThrow().isVisible()).isTrue();
        assertThat(fixture.stored().get().getRollingAveragePresets().getFirst().getName()).isEqualTo("First");
        fixture.stored().get().getRollingAveragePresets().removeFirst();
        clearInvocations(fixture.service());
        button(fixture.view(), "Save changes").click();
        verify(fixture.service(), never()).saveSettings(any());
        assertThat(fixture.stored().get().getRollingAveragePresets()).extracting(RollingAveragePreset::getName)
                .containsExactly("Second");
    }

    private record Fixture(SettingsView view, UserSettingsService service, AtomicReference<UserSettings> stored) { }

    private static Fixture fixture() {
        UserSettings initial = new UserSettings();
        initial.setRollingAveragePresets(new ArrayList<>(List.of(preset("First"), preset("Second"))));
        AtomicReference<UserSettings> stored = new AtomicReference<>(initial);
        UserSettingsService service = mock(UserSettingsService.class);
        when(service.getSettings()).thenAnswer(invocation -> copy(stored.get()));
        doAnswer(invocation -> {
            stored.set(copy(invocation.getArgument(0)));
            return null;
        }).when(service).saveSettings(any(UserSettings.class));
        return new Fixture(new SettingsView(mock(MigrationClient.class), service), service, stored);
    }

    private static TextField nameField(Component root) {
        return components(root).filter(TextField.class::isInstance).map(TextField.class::cast).findFirst().orElseThrow();
    }

    private static DatePicker dateField(Component root, String label) {
        return components(root).filter(DatePicker.class::isInstance).map(DatePicker.class::cast)
                .filter(field -> field.getLabel().equals(label)).findFirst().orElseThrow();
    }

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
                .filter(button -> button.getAriaLabel().orElse("").equals(label) || button.getText().equals(label))
                .findFirst().orElseThrow();
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
