package dev.itobey.adapter.api.fddb.exporter.ui.views;

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

import java.util.List;

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
        section.addClassNames(LumoUtility.Padding.LARGE);
        section.setAlignItems(Alignment.START);
        section.setWidthFull();

        Div sectionHeader = new Div();
        sectionHeader.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.MEDIUM);
        sectionHeader.getStyle().set("flex-wrap", "wrap").set("margin-bottom", "1.5rem");

        Icon icon = new Icon(VaadinIcon.CALENDAR_CLOCK);
        icon.setSize("24px");
        icon.addClassNames(LumoUtility.TextColor.PRIMARY);

        Span sectionTitle = new Span("Rolling Average Presets");
        sectionTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.SEMIBOLD);
        sectionHeader.add(icon, sectionTitle);

        Paragraph description = new Paragraph(
                "Define custom date range presets that appear as quick-select buttons on the Rolling Averages view."
        );
        description.addClassNames(LumoUtility.TextColor.SECONDARY);
        description.getStyle().set("margin-top", "0");

        presetsListDiv = new Div();
        presetsListDiv.setWidthFull();
        refreshPresetsList();

        VerticalLayout addForm = createAddPresetForm();

        section.add(sectionHeader, description, presetFeedback, presetsListDiv, new H3("Add New Preset"), addForm);
        return section;
    }

    private void refreshPresetsList() {
        presetsListDiv.removeAll();
        List<RollingAveragePreset> presets = userSettingsService.getSettings().getRollingAveragePresets();

        if (presets.isEmpty()) {
            Paragraph empty = new Paragraph("No custom presets defined yet.");
            empty.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
            presetsListDiv.add(empty);
            return;
        }

        for (RollingAveragePreset preset : presets) {
            Div row = new Div();
            row.addClassNames(LumoUtility.Padding.SMALL, LumoUtility.BorderRadius.SMALL, LumoUtility.Background.CONTRAST_5);
            row.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "1fr auto")
                    .set("align-items", "center")
                    .set("column-gap", "var(--lumo-space-m)")
                    .set("margin-bottom", "0.5rem");

            Div infoDiv = new Div();

            Span nameSpan = new Span(preset.getName());
            nameSpan.addClassNames(LumoUtility.FontWeight.SEMIBOLD);
            nameSpan.getStyle().set("display", "block").set("word-break", "break-word");

            Span rangeSpan = new Span(preset.getFromDate() + " → " + preset.getToDate());
            rangeSpan.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
            rangeSpan.getStyle().set("display", "block").set("word-break", "break-word");

            infoDiv.add(nameSpan, rangeSpan);

            Button deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            deleteButton.setTooltipText("Delete preset");
            deleteButton.setAriaLabel("Delete preset " + preset.getName());
            deleteButton.addClassName("preset-delete-button");
            deleteButton.addClickListener(e -> deletePreset(preset.getName()));

            row.add(infoDiv, deleteButton);
            presetsListDiv.add(row);
        }
    }

    private VerticalLayout createAddPresetForm() {
        VerticalLayout form = new VerticalLayout();
        form.setPadding(false);
        form.setSpacing(true);

        TextField nameField = new TextField("Preset Name");
        nameField.setPlaceholder("e.g. Q1 2025");
        nameField.setWidthFull();

        DatePicker fromDatePicker = new DatePicker("From Date");
        fromDatePicker.setI18n(createDatePickerI18n());
        fromDatePicker.setRequired(true);

        DatePicker toDatePicker = new DatePicker("To Date");
        toDatePicker.setI18n(createDatePickerI18n());
        toDatePicker.setRequired(true);

        FormLayout dateLayout = new FormLayout(fromDatePicker, toDatePicker);
        dateLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );
        dateLayout.setWidthFull();

        Button addButton = new Button("Add Preset", new Icon(VaadinIcon.PLUS));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> {
            String name = nameField.getValue().trim();
            nameField.setInvalid(false);
            if (name.isEmpty()) {
                nameField.setErrorMessage("Enter a name for this preset.");
                nameField.setInvalid(true);
                return;
            }
            if (!validateDateRange(fromDatePicker, toDatePicker, false)) {
                return;
            }
            boolean nameExists = userSettingsService.getSettings().getRollingAveragePresets().stream()
                    .anyMatch(p -> p.getName().equalsIgnoreCase(name));
            if (nameExists) {
                nameField.setErrorMessage("A preset with this name already exists. Choose another name.");
                nameField.setInvalid(true);
                return;
            }
            if (!addPreset(name, fromDatePicker.getValue(), toDatePicker.getValue())) {
                return;
            }
            nameField.clear();
            fromDatePicker.clear();
            fromDatePicker.setInvalid(false);
            toDatePicker.clear();
            toDatePicker.setInvalid(false);
        });

        form.add(nameField, dateLayout, addButton);
        return form;
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
