package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.StatsClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

@Route(value = "averages", layout = MainLayout.class)
@PageTitle("Rolling Averages | FDDB Exporter")
public class RollingAveragesView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String BG_COLOR = "rgba(78, 97, 155, 0.08)";
    private static final String HIGHLIGHT_COLOR = "#ae9357";

    private final StatsClient statsClient;
    private final FddbExporterProperties properties;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private Div resultDiv;

    public RollingAveragesView(StatsClient statsClient, FddbExporterProperties properties) {
        this.statsClient = statsClient;
        this.properties = properties;

        addClassName("rolling-averages-view");
        setSpacing(true);
        setPadding(true);
        applyResponsivePadding(this);

        add(new H2("Rolling Averages"));
        add(new Paragraph("View average nutritional values over a specified date range."));

        if (!isMongoDbEnabled(properties)) {
            add(createMongoDbDisabledWarning("Rolling Averages"));
            return;
        }

        add(createDateRangeForm());
        add(createResultSection());
    }

    private VerticalLayout createDateRangeForm() {
        VerticalLayout section = createSection(null);
        section.add(new H3("Select Date Range"));

        FormLayout form = new FormLayout();

        fromDatePicker = new DatePicker("From Date");
        fromDatePicker.setValue(LocalDate.now().minusMonths(1));
        fromDatePicker.setRequired(true);
        fromDatePicker.setI18n(createDatePickerI18n());

        toDatePicker = new DatePicker("To Date");
        toDatePicker.setValue(LocalDate.now().minusDays(1));
        toDatePicker.setRequired(true);
        toDatePicker.setI18n(createDatePickerI18n());

        form.add(fromDatePicker, toDatePicker);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );

        HorizontalLayout quickButtons = new HorizontalLayout();
        quickButtons.addClassName("preset-buttons-container");
        quickButtons.addClassNames(LumoUtility.Gap.SMALL);
        quickButtons.setWidthFull();
        quickButtons.getStyle().set("flex-wrap", "wrap");
        quickButtons.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

        quickButtons.add(
                createPresetButton("Last 7 Days", 7),
                createPresetButton("Last 30 Days", 30),
                createPresetButton("Last 90 Days", 90),
                createPresetButton("Last Year", 365),
                createYearButton()
        );

        Button calculateButton = new Button("Calculate Averages");
        calculateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        calculateButton.addClickListener(e -> calculateAverages());
        calculateButton.setWidthFull();

        section.add(form, quickButtons, calculateButton);
        return section;
    }

    private Button createPresetButton(String label, int days) {
        Button button = new Button(label, e -> {
            setDateRange(days);
            calculateAverages();
        });
        button.addThemeVariants(ButtonVariant.LUMO_SMALL);
        button.addClassName("preset-btn");
        button.getStyle()
                .set("flex", "1 1 calc(50% - 0.25rem)")
                .set("min-width", "calc(50% - 0.25rem)")
                .set("color", HIGHLIGHT_COLOR);
        return button;
    }

    private Button createYearButton() {
        Button button = new Button("Current Year", e -> {
            setCurrentYearRange();
            calculateAverages();
        });
        button.addThemeVariants(ButtonVariant.LUMO_SMALL);
        button.addClassName("preset-btn");
        button.getStyle()
                .set("flex", "1 1 calc(50% - 0.25rem)")
                .set("min-width", "calc(50% - 0.25rem)")
                .set("color", HIGHLIGHT_COLOR);
        return button;
    }

    private void setDateRange(int days) {
        toDatePicker.setValue(LocalDate.now().minusDays(1));
        fromDatePicker.setValue(LocalDate.now().minusDays(days));
    }

    private void setCurrentYearRange() {
        fromDatePicker.setValue(LocalDate.of(LocalDate.now().getYear(), 1, 1));
        toDatePicker.setValue(LocalDate.now().minusDays(1));
    }

    private VerticalLayout createResultSection() {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(true);
        section.setPadding(false);

        resultDiv = new Div();
        resultDiv.setWidthFull();
        resultDiv.setVisible(false);

        section.add(resultDiv);
        return section;
    }

    private void calculateAverages() {
        if (fromDatePicker.getValue() == null || toDatePicker.getValue() == null) {
            showError("Please select both from and to dates");
            return;
        }

        if (fromDatePicker.getValue().isAfter(toDatePicker.getValue())) {
            showError("From date must be before or equal to to date");
            return;
        }

        try {
            String fromDate = fromDatePicker.getValue().format(DATE_FORMAT);
            String toDate = toDatePicker.getValue().format(DATE_FORMAT);

            RollingAveragesDTO result = statsClient.getRollingAverages(fromDate, toDate);
            displayResult(result);
            showSuccess("Averages calculated successfully");
        } catch (ApiException e) {
            showError(e.getMessage());
        }
    }

    private void displayResult(RollingAveragesDTO result) {
        resultDiv.removeAll();
        resultDiv.setVisible(true);

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);

        if (result.getAverages() != null) {
            H3 heading = new H3(result.getFromDate() + " ⮕ " + result.getToDate());
            content.add(heading);

            Div averagesGrid = createCardsGrid("120px");
            StatsDTO.Averages avg = result.getAverages();

            averagesGrid.add(
                    createNutrientCard("Calories", formatNumber(avg.getAvgTotalCalories()), "kcal", "🔥", BG_COLOR),
                    createNutrientCard("Fat", formatNumber(avg.getAvgTotalFat()), "g", "🧈", BG_COLOR),
                    createNutrientCard("Carbs", formatNumber(avg.getAvgTotalCarbs()), "g", "🍞", BG_COLOR),
                    createNutrientCard("Sugar", formatNumber(avg.getAvgTotalSugar()), "g", "🍬", BG_COLOR),
                    createNutrientCard("Protein", formatNumber(avg.getAvgTotalProtein()), "g", "🥩", BG_COLOR),
                    createNutrientCard("Fibre", formatNumber(avg.getAvgTotalFibre()), "g", "🥦", BG_COLOR)
            );

            content.add(averagesGrid);
            content.add(new H3("Macro Distribution"));
            content.add(createMacroDistributionBars(avg));
        }

        resultDiv.add(content);
    }

    private Component createMacroDistributionBars(StatsDTO.Averages avg) {
        VerticalLayout container = new VerticalLayout();
        container.addClassNames(LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);
        container.setSpacing(true);
        container.setWidthFull();
        container.setPadding(true);
        container.getStyle().set("padding", "1rem").set("box-sizing", "border-box");

        double totalFat = avg.getAvgTotalFat();
        double totalCarbs = avg.getAvgTotalCarbs();
        double totalProtein = avg.getAvgTotalProtein();
        double totalMacros = totalFat + totalCarbs + totalProtein;

        if (totalMacros > 0) {
            double fatPercentage = (totalFat / totalMacros) * 100;
            double carbsPercentage = (totalCarbs / totalMacros) * 100;
            double proteinPercentage = (totalProtein / totalMacros) * 100;

            Div stackedBar = new Div();
            stackedBar.setWidthFull();
            stackedBar.setHeight("60px");  // Increased height for two-line labels
            stackedBar.addClassNames(LumoUtility.BorderRadius.MEDIUM);
            stackedBar.getStyle().set("display", "flex");
            stackedBar.getStyle().set("flex-direction", "row");
            stackedBar.getStyle().set("flex-wrap", "nowrap");
            stackedBar.getStyle().set("overflow", "hidden");

            Div fatSection = new Div();
            fatSection.setWidth(fatPercentage + "%");
            fatSection.setHeight("100%");
            fatSection.getStyle().set("flex", "0 0 " + fatPercentage + "%");
            fatSection.getStyle().set("min-width", "0 !important");
            fatSection.getStyle().set("box-sizing", "border-box");
            fatSection.getStyle().set("background-color", "#FFE66D");
            fatSection.getStyle().set("display", "flex").set("justify-content", "center").set("align-items", "center");
            fatSection.getStyle().set("flex-direction", "column");  // Stack label parts vertically
            fatSection.getStyle().set("padding", "0.25rem");
            Span fatLabel = new Span("Fat " + formatNumber(fatPercentage) + "%");
            fatLabel.getStyle()
                    .set("width", "100%")
                    .set("text-align", "center")
                    .set("word-wrap", "break-word")
                    .set("overflow-wrap", "break-word")
                    .set("color", "var(--accent-active)")
                    .set("font-weight", "600")
                    .set("font-size", "0.85rem")
                    .set("line-height", "1.2");
            fatSection.add(fatLabel);

            Div carbsSection = new Div();
            carbsSection.setWidth(carbsPercentage + "%");
            carbsSection.setHeight("100%");
            carbsSection.getStyle().set("flex", "0 0 " + carbsPercentage + "%");
            carbsSection.getStyle().set("min-width", "0 !important");
            carbsSection.getStyle().set("box-sizing", "border-box");
            carbsSection.getStyle().set("background-color", "#4ECDC4");
            carbsSection.getStyle().set("display", "flex").set("justify-content", "center").set("align-items", "center");
            carbsSection.getStyle().set("flex-direction", "column");
            carbsSection.getStyle().set("padding", "0.25rem");
            Span carbsLabel = new Span("Carbs " + formatNumber(carbsPercentage) + "%");
            carbsLabel.getStyle()
                    .set("width", "100%")
                    .set("text-align", "center")
                    .set("word-wrap", "break-word")
                    .set("overflow-wrap", "break-word")
                    .set("color", "var(--accent-active)")
                    .set("font-weight", "600")
                    .set("font-size", "0.85rem")
                    .set("line-height", "1.2");
            carbsSection.add(carbsLabel);

            Div proteinSection = new Div();
            proteinSection.setWidth(proteinPercentage + "%");
            proteinSection.setHeight("100%");
            proteinSection.getStyle().set("flex", "0 0 " + proteinPercentage + "%");
            proteinSection.getStyle().set("min-width", "0 !important");
            proteinSection.getStyle().set("box-sizing", "border-box");
            proteinSection.getStyle().set("background-color", "#e5769f");
            proteinSection.getStyle().set("display", "flex").set("justify-content", "center").set("align-items", "center");
            proteinSection.getStyle().set("flex-direction", "column");
            proteinSection.getStyle().set("padding", "0.25rem");
            Span proteinLabel = new Span("Protein " + formatNumber(proteinPercentage) + "%");
            proteinLabel.getStyle()
                    .set("width", "100%")
                    .set("text-align", "center")
                    .set("word-wrap", "break-word")
                    .set("overflow-wrap", "break-word")
                    .set("color", "var(--accent-active)")
                    .set("font-weight", "600")
                    .set("font-size", "0.85rem")
                    .set("line-height", "1.2");
            proteinSection.add(proteinLabel);

            stackedBar.add(fatSection, carbsSection, proteinSection);

            container.add(stackedBar);
        } else {
            Paragraph noData = new Paragraph("No macro data available");
            noData.addClassNames(LumoUtility.TextColor.SECONDARY);
            container.add(noData);
        }

        return container;
    }


    private void showSuccess(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        notification.setDuration(3000);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setDuration(5000);
    }
}
