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
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private Div resultDiv;

    public RollingAveragesView(StatsClient statsClient) {
        this.statsClient = statsClient;

        addClassName("rolling-averages-view");
        setSpacing(true);
        setPadding(true);
        applyResponsivePadding(this);

        add(new H2("Rolling Averages"));
        add(new Paragraph("View average nutritional values over a specified date range."));
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
            H3 heading = new H3("Averages for " + result.getFromDate() + " to " + result.getToDate());
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
        VerticalLayout bars = new VerticalLayout();
        bars.addClassNames(LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);
        bars.setSpacing(true);
        bars.setWidthFull();
        bars.setPadding(true);
        bars.getStyle().set("padding", "1rem").set("box-sizing", "border-box");

        double totalFat = avg.getAvgTotalFat();
        double totalCarbs = avg.getAvgTotalCarbs();
        double totalProtein = avg.getAvgTotalProtein();
        double totalMacros = totalFat + totalCarbs + totalProtein;

        if (totalMacros > 0) {
            double fatPercentage = (totalFat / totalMacros) * 100;
            double carbsPercentage = (totalCarbs / totalMacros) * 100;
            double proteinPercentage = (totalProtein / totalMacros) * 100;

            bars.add(createMacroProgressBar("Fat", totalFat, fatPercentage, "#FFE66D"));
            bars.add(createMacroProgressBar("Carbs", totalCarbs, carbsPercentage, "#4ECDC4"));
            bars.add(createMacroProgressBar("Protein", totalProtein, proteinPercentage, "#95E1D3"));
        } else {
            Paragraph noData = new Paragraph("No macro data available");
            noData.addClassNames(LumoUtility.TextColor.SECONDARY);
            bars.add(noData);
        }

        return bars;
    }

    private Component createMacroProgressBar(String label, double grams, double percentage, String color) {
        VerticalLayout container = new VerticalLayout();
        container.setWidthFull();
        container.setPadding(false);
        container.setSpacing(false);
        container.getStyle().set("gap", "0.25rem");

        HorizontalLayout topRow = new HorizontalLayout();
        topRow.setWidthFull();
        topRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topRow.setAlignItems(FlexComponent.Alignment.CENTER);
        topRow.setPadding(false);
        topRow.setSpacing(false);

        Span labelSpan = new Span(label);
        labelSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD);

        Span valueSpan = new Span(formatNumber(grams) + "g (" + formatNumber(percentage) + "%)");
        valueSpan.addClassNames(LumoUtility.FontSize.SMALL);

        topRow.add(labelSpan, valueSpan);

        Div progressContainer = new Div();
        progressContainer.setWidthFull();
        progressContainer.addClassNames(LumoUtility.Background.CONTRAST_10, LumoUtility.BorderRadius.SMALL);
        progressContainer.setHeight("20px");
        progressContainer.getStyle().set("position", "relative");

        Div progressFill = new Div();
        progressFill.setWidth(percentage + "%");
        progressFill.setHeight("100%");
        progressFill.addClassNames(LumoUtility.BorderRadius.SMALL);
        progressFill.getStyle().set("background-color", color);

        progressContainer.add(progressFill);

        container.add(topRow, progressContainer);
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
