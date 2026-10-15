package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.domain.RollingAveragePreset;
import dev.itobey.adapter.api.fddb.exporter.domain.UserSettings;
import dev.itobey.adapter.api.fddb.exporter.service.UserSettingsService;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.MigrationClient;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

@Route(value = "settings", layout = MainLayout.class)
@PageTitle("Settings | FDDB Exporter")
public class SettingsView extends VerticalLayout {

    private final MigrationClient migrationClient;
    private final UserSettingsService userSettingsService;
    private Div resultDiv;
    private ProgressBar progressBar;
    private Div presetsListDiv;
    private final Div presetFeedback = createFeedbackRegion();
    private static final DateTimeFormatter PRESET_DATE = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.UK);
    private final Span presetCount = new Span();
    private final TextField presetName = new TextField("Preset Name");
    private final DatePicker presetFrom = new DatePicker("From Date");
    private final DatePicker presetTo = new DatePicker("To Date");
    private final H3 presetEditorTitle = new H3("Add New Preset");
    private final Button savePresetButton = new Button("Add Preset");
    private final Button newPresetButton = new Button("Add preset", new Icon(VaadinIcon.PLUS));
    private VerticalLayout presetEditor;
    private String editingPresetName;

    public SettingsView(MigrationClient migrationClient,
                        @org.springframework.beans.factory.annotation.Autowired(required = false) UserSettingsService userSettingsService) {
        this.migrationClient = migrationClient;
        this.userSettingsService = userSettingsService;

        addClassName("settings-view");
        setSpacing(true);
        setPadding(true);
        applyResponsivePadding(this);

        add(new H2("Settings"));
        add(new Paragraph("Manage application settings and data operations."));

        Div sectionsContainer = new Div();
        sectionsContainer.addClassName("settings-sections-grid");
        sectionsContainer.setWidthFull();

        if (userSettingsService != null) {
            sectionsContainer.add(createPresetsSection());
        }
        sectionsContainer.add(createMigrationSection());
        add(sectionsContainer);
    }

    private VerticalLayout createMigrationSection() {
        VerticalLayout section = createSection(null);
        section.addClassNames(LumoUtility.Padding.LARGE);
        section.setAlignItems(Alignment.START);
        section.setWidthFull();

        Div sectionHeader = new Div();
        sectionHeader.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.MEDIUM);
        sectionHeader.getStyle().set("flex-wrap", "wrap").set("margin-bottom", "1.5rem");

        Icon settingsIcon = new Icon(VaadinIcon.COGS);
        settingsIcon.setSize("24px");
        settingsIcon.addClassNames(LumoUtility.TextColor.PRIMARY);

        Span sectionTitle = new Span("Data Operations");
        sectionTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.SEMIBOLD);
        sectionHeader.add(settingsIcon, sectionTitle);

        // Migration subsection
        Div migrationCard = new Div();
        migrationCard.addClassNames(LumoUtility.Padding.LARGE, LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.CONTRAST_5, LumoUtility.BoxShadow.SMALL);
        migrationCard.setWidthFull();
        migrationCard.addClassName("settings-card");

        VerticalLayout cardContent = new VerticalLayout();
        cardContent.setPadding(false);
        cardContent.setSpacing(true);

        Div header = new Div();
        header.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.MEDIUM);
        header.getStyle().set("flex-wrap", "wrap");

        Icon dbIcon = new Icon(VaadinIcon.DATABASE);
        dbIcon.setSize("32px");
        dbIcon.addClassNames(LumoUtility.TextColor.PRIMARY);

        Span title = new Span("MongoDB → InfluxDB Migration");
        title.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.SEMIBOLD);
        header.add(dbIcon, title);

        Paragraph description = new Paragraph(
                "Migrate all stored FDDB data from MongoDB to InfluxDB. " +
                        "This is useful for time-series analysis and visualization in tools like Grafana."
        );
        description.addClassNames(LumoUtility.TextColor.SECONDARY);

        Div warningNotice = new Div();
        warningNotice.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.BorderRadius.SMALL,
                LumoUtility.Background.WARNING_10);
        Span warningText = new Span("⚠️ Both MongoDB and InfluxDB must be available and properly configured.");
        warningText.addClassNames(LumoUtility.FontSize.SMALL);
        warningNotice.add(warningText);

        Button migrateButton = new Button("Start Migration");
        migrateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        migrateButton.setIcon(new Icon(VaadinIcon.PLAY));
        migrateButton.addClickListener(e -> confirmMigration());
        migrateButton.addClassName("settings-action-button");

        progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setWidthFull();

        resultDiv = new Div();
        resultDiv.setVisible(false);
        resultDiv.setWidthFull();

        cardContent.add(header, description, warningNotice, migrateButton, progressBar, resultDiv);
        migrationCard.add(cardContent);
        section.add(sectionHeader, migrationCard);

        return section;
    }

    private void confirmMigration() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Confirm Migration");
        dialog.setText("Are you sure you want to migrate all MongoDB entries to InfluxDB? " +
                "This may take some time depending on the amount of data.");

        dialog.setCancelable(true);
        dialog.setConfirmText("Start Migration");
        dialog.setConfirmButtonTheme("primary");

        dialog.addConfirmListener(e -> runMigration());

        dialog.open();
    }

    private void runMigration() {
        progressBar.setVisible(true);
        resultDiv.setVisible(false);

        try {
            String result = migrationClient.migrateToInfluxDb();
            displayResult(result, true);
            showSuccess("Migration completed successfully");
        } catch (ApiException e) {
            displayResult(e.getMessage(), false);
            showError("Migration failed: " + e.getMessage());
        } finally {
            progressBar.setVisible(false);
        }
    }

    private void displayResult(String message, boolean success) {
        resultDiv.removeAll();
        resultDiv.setVisible(true);

        Div resultCard = new Div();
        resultCard.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.BorderRadius.SMALL,
                LumoUtility.Margin.Top.MEDIUM);

        if (success) {
            resultCard.addClassNames(LumoUtility.Background.SUCCESS_10);
            Icon successIcon = new Icon(VaadinIcon.CHECK_CIRCLE);
            successIcon.addClassNames(LumoUtility.TextColor.SUCCESS);
            Span resultText = new Span(message);
            resultText.addClassNames(LumoUtility.TextColor.SUCCESS);

            Div content = new Div();
            content.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.SMALL);
            content.add(successIcon, resultText);
            resultCard.add(content);
        } else {
            resultCard.addClassNames(LumoUtility.Background.ERROR_10);
            Icon errorIcon = new Icon(VaadinIcon.EXCLAMATION_CIRCLE);
            errorIcon.addClassNames(LumoUtility.TextColor.ERROR);
            Span resultText = new Span(message);
            resultText.addClassNames(LumoUtility.TextColor.ERROR);

            Div content = new Div();
            content.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.SMALL);
            content.add(errorIcon, resultText);
            resultCard.add(content);
        }

        resultDiv.add(resultCard);
    }

    private VerticalLayout createPresetsSection() {
        VerticalLayout section = createSection(null);
        section.addClassName("preset-ledger-section");
        section.setSpacing(false);
        section.setAlignItems(Alignment.START);
        section.setWidthFull();

        Div sectionHeader = new Div();
        sectionHeader.addClassName("preset-ledger-heading");
        H3 title = new H3();
        title.add(new Icon(VaadinIcon.CALENDAR_CLOCK), new Span("Rolling Average Presets"), presetCount);
        presetCount.addClassName("preset-ledger-count");
        Paragraph description = new Paragraph("Saved date ranges for quick selection in Rolling Averages.");
        Div headingText = new Div(title, description);
        newPresetButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newPresetButton.setAriaLabel("Add preset");
        newPresetButton.getElement().setAttribute("aria-controls", "preset-editor");
        newPresetButton.getElement().setAttribute("aria-expanded", "false");
        newPresetButton.addClickListener(e -> openPresetEditor(null));
        sectionHeader.add(headingText, newPresetButton);

        presetsListDiv = new Div();
        presetsListDiv.setWidthFull();
        refreshPresetsList();
        presetEditor = createAddPresetForm();
        presetEditor.setVisible(false);
        Div footer = new Div(new Icon(VaadinIcon.LINK),
                new Span("Available as quick-select buttons in Rolling Averages."));
        footer.addClassName("preset-ledger-footer");
        section.add(sectionHeader, presetFeedback, presetsListDiv, presetEditor, footer);
        return section;
    }

    private void refreshPresetsList() {
        presetsListDiv.removeAll();
        List<RollingAveragePreset> presets = userSettingsService.getSettings().getRollingAveragePresets();
        presetCount.setText("(" + presets.size() + ")");

        if (presets.isEmpty()) {
            Paragraph empty = new Paragraph("No presets yet. Add a date range to reuse it in Rolling Averages.");
            empty.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
            empty.addClassName("preset-ledger-empty");
            presetsListDiv.add(empty);
            return;
        }

        Div table = new Div();
        table.addClassName("preset-ledger");
        table.getElement().setAttribute("role", "table");
        table.getElement().setAttribute("aria-label", "Rolling average presets");
        Div header = new Div();
        header.addClassNames("preset-ledger-row", "preset-ledger-labels");
        header.getElement().setAttribute("role", "row");
        header.add(ledgerCell(new Span("Preset"), "preset-ledger-name", true),
                ledgerCell(new Span("Date range"), "preset-ledger-range", true),
                ledgerCell(new Span("Days"), "preset-ledger-days", true),
                ledgerCell(new Span("Actions"), "preset-ledger-actions-label", true));
        table.add(header);
        for (RollingAveragePreset preset : presets) {
            Div row = new Div();
            row.addClassName("preset-ledger-row");
            row.getElement().setAttribute("role", "row");
            Span rangeSpan = new Span(PRESET_DATE.format(preset.getFromDate()) + " → " + PRESET_DATE.format(preset.getToDate()));
            Span days = new Span(Long.toString(ChronoUnit.DAYS.between(preset.getFromDate(), preset.getToDate()) + 1));
            days.getElement().setAttribute("title", "Calendar days, including both start and end dates");
            Button editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            editButton.setTooltipText("Edit preset");
            editButton.setAriaLabel("Edit preset " + preset.getName());
            editButton.addClickListener(e -> openPresetEditor(preset));

            Button deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            deleteButton.setTooltipText("Delete preset");
            deleteButton.setAriaLabel("Delete preset " + preset.getName());
            deleteButton.addClassName("preset-delete-button");
            deleteButton.addClickListener(e -> deletePreset(preset.getName()));

            Div actions = new Div(editButton, deleteButton);
            row.add(ledgerCell(new Span(preset.getName()), "preset-ledger-name", false),
                    ledgerCell(rangeSpan, "preset-ledger-range", false),
                    ledgerCell(days, "preset-ledger-days", false),
                    ledgerCell(actions, "preset-ledger-actions", false));
            table.add(row);
        }
        presetsListDiv.add(table);
    }

    private Div ledgerCell(Component content, String className, boolean header) {
        Div cell = new Div(content);
        cell.addClassName(className);
        cell.getElement().setAttribute("role", header ? "columnheader" : "cell");
        return cell;
    }

    private void openPresetEditor(RollingAveragePreset preset) {
        editingPresetName = preset == null ? null : preset.getName();
        presetEditorTitle.setText(preset == null ? "Add New Preset" : "Edit preset");
        savePresetButton.setText(preset == null ? "Add Preset" : "Save changes");
        presetName.setValue(preset == null ? "" : preset.getName());
        presetFrom.setValue(preset == null ? null : preset.getFromDate());
        presetTo.setValue(preset == null ? null : preset.getToDate());
        presetName.setInvalid(false);
        presetFrom.setInvalid(false);
        presetTo.setInvalid(false);
        presetEditor.setVisible(true);
        newPresetButton.getElement().setAttribute("aria-expanded", "true");
        presetName.focus();
    }

    private void closePresetEditor() {
        presetEditor.setVisible(false);
        editingPresetName = null;
        newPresetButton.getElement().setAttribute("aria-expanded", "false");
        newPresetButton.focus();
    }

    private VerticalLayout createAddPresetForm() {
        VerticalLayout form = new VerticalLayout();
        form.setId("preset-editor");
        form.addClassName("preset-ledger-editor");
        form.setPadding(false);
        form.setSpacing(true);
        form.setWidthFull();
        presetName.setPlaceholder("e.g. Q1 2026");
        presetName.setRequiredIndicatorVisible(true);
        presetFrom.setI18n(createDatePickerI18n());
        presetFrom.setRequired(true);
        presetTo.setI18n(createDatePickerI18n());
        presetTo.setRequired(true);
        FormLayout fields = new FormLayout(presetName, presetFrom, presetTo);
        fields.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("480px", 2),
                new FormLayout.ResponsiveStep("760px", 3)
        );
        fields.setWidthFull();
        savePresetButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        savePresetButton.addClickListener(e -> {
            String name = presetName.getValue().trim();
            presetName.setInvalid(false);
            if (name.isEmpty()) {
                presetName.setErrorMessage("Enter a name for this preset.");
                presetName.setInvalid(true);
                return;
            }
            if (!validateDateRange(presetFrom, presetTo, false)) {
                return;
            }
            boolean nameExists = userSettingsService.getSettings().getRollingAveragePresets().stream()
                    .anyMatch(p -> !p.getName().equals(editingPresetName) && p.getName().equalsIgnoreCase(name));
            if (nameExists) {
                presetName.setErrorMessage("A preset with this name already exists. Choose another name.");
                presetName.setInvalid(true);
                return;
            }
            boolean saved = editingPresetName == null
                    ? addPreset(name, presetFrom.getValue(), presetTo.getValue())
                    : updatePreset(editingPresetName, name, presetFrom.getValue(), presetTo.getValue());
            if (!saved) {
                return;
            }
            closePresetEditor();
        });
        Button cancel = new Button("Cancel", e -> closePresetEditor());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Div actions = new Div(savePresetButton, cancel);
        actions.addClassName("preset-ledger-form-actions");
        form.add(presetEditorTitle, fields, actions);
        return form;
    }

    private boolean updatePreset(String originalName, String name, java.time.LocalDate from, java.time.LocalDate to) {
        UserSettings settings = userSettingsService.getSettings();
        List<RollingAveragePreset> presets = settings.getRollingAveragePresets();
        int index = -1;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).getName().equals(originalName)) {
                index = i;
            } else if (presets.get(i).getName().equalsIgnoreCase(name)) {
                presetName.setErrorMessage("A preset with this name already exists. Choose another name.");
                presetName.setInvalid(true);
                return false;
            }
        }
        if (index < 0) {
            showError("This preset was removed. Cancel and add it as a new preset.");
            refreshPresetsList();
            return false;
        }
        RollingAveragePreset updated = new RollingAveragePreset();
        updated.setName(name);
        updated.setFromDate(from);
        updated.setToDate(to);
        presets.set(index, updated);
        boolean saved = trySaveSettings(settings, "Could not update preset \"" + originalName + "\"");
        refreshPresetsList();
        if (saved) {
            showSuccess("Preset \"" + name + "\" updated.");
        }
        return saved;
    }

    private boolean addPreset(String name, java.time.LocalDate fromDate, java.time.LocalDate toDate) {
        UserSettings settings = userSettingsService.getSettings();
        boolean nameExists = settings.getRollingAveragePresets().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(name));
        if (nameExists) {
            showError("A preset with this name already exists.");
            return false;
        }
        RollingAveragePreset preset = new RollingAveragePreset();
        preset.setName(name);
        preset.setFromDate(fromDate);
        preset.setToDate(toDate);
        settings.getRollingAveragePresets().add(preset);
        boolean saved = trySaveSettings(settings, "Could not add preset \"" + name + "\"");
        refreshPresetsList();
        if (saved) {
            showSuccess("Preset \"" + name + "\" added.");
        }
        return saved;
    }

    private void deletePreset(String name) {
        UserSettings settings = userSettingsService.getSettings();
        List<RollingAveragePreset> presets = settings.getRollingAveragePresets();
        RollingAveragePreset removed = presets.stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
        if (removed == null) {
            refreshPresetsList();
            return;
        }
        int originalIndex = presets.indexOf(removed);
        settings.getRollingAveragePresets().removeIf(p -> p.getName().equals(name));
        boolean saved = trySaveSettings(settings, "Could not delete preset \"" + name + "\"");
        refreshPresetsList();
        if (saved) {
            if (name.equals(editingPresetName)) {
                closePresetEditor();
            }
            showEmptyState(presetFeedback, "Preset \"" + name + "\" deleted.");
            Button undoButton = new Button("Undo", e -> restorePreset(removed, originalIndex));
            undoButton.setAriaLabel("Undo deletion of preset " + name);
            presetFeedback.add(undoButton);
        }
    }

    private void restorePreset(RollingAveragePreset preset, int originalIndex) {
        UserSettings settings = userSettingsService.getSettings();
        List<RollingAveragePreset> presets = settings.getRollingAveragePresets();
        if (presets.stream().anyMatch(p -> p.getName().equalsIgnoreCase(preset.getName()))) {
            showError("A preset named \"" + preset.getName() + "\" already exists. Undo will not replace it.");
            return;
        }
        presets.add(Math.min(originalIndex, presets.size()), preset);
        if (trySaveSettings(settings, "Could not restore preset \"" + preset.getName() + "\"")) {
            refreshPresetsList();
            showEmptyState(presetFeedback, "Preset \"" + preset.getName() + "\" restored.");
        }
    }

    /**
     * Persists the settings, reporting a failure to the user instead of letting the caller claim success.
     *
     * @return true if the settings were persisted
     */
    private boolean trySaveSettings(UserSettings settings, String failureMessage) {
        try {
            userSettingsService.saveSettings(settings);
            return true;
        } catch (RuntimeException exception) {
            showError(failureMessage + ": " + exception.getMessage());
            return false;
        }
    }
}
