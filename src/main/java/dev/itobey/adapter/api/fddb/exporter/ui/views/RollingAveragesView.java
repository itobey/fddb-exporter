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

/**
 * View for displaying rolling averages of nutritional data.
 */
@Route(value = "averages", layout = MainLayout.class)
@PageTitle("Rolling Averages | FDDB Exporter")
public class RollingAveragesView extends VerticalLayout {

    private final StatsClient statsClient;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private Div resultDiv;

    public RollingAveragesView(StatsClient statsClient) {
        this.statsClient = statsClient;

        addClassName("rolling-averages-view");
        setSpacing(true);
        setPadding(true);
        // Responsive padding - minimum on mobile for spacing from edges
        getStyle().set("padding", "clamp(0.5rem, 2vw, 1.5rem)");

        add(new H2("Rolling Averages"));
        add(new Paragraph("View average nutritional values over a specified date range."));

        add(createDateRangeForm());
        add(createResultSection());
    }

    private VerticalLayout createDateRangeForm() {
        VerticalLayout section = new VerticalLayout();
        section.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.CONTRAST_5
        );
        section.setSpacing(true);

        section.add(new H3("Select Date Range"));

        FormLayout form = new FormLayout();

        fromDatePicker = new DatePicker("From Date");
        fromDatePicker.setValue(LocalDate.now().minusMonths(1));
        fromDatePicker.setRequired(true);
        fromDatePicker.setI18n(createDatePickerI18n());

        toDatePicker = new DatePicker("To Date");
        toDatePicker.setValue(LocalDate.now().minusDays(1)); // Set to yesterday by default
        toDatePicker.setRequired(true);
        toDatePicker.setI18n(createDatePickerI18n());

        form.add(fromDatePicker, toDatePicker);
        // Responsive: 1 column on mobile, 2 on desktop
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );

        // Quick selection buttons
        HorizontalLayout quickButtons = new HorizontalLayout();
        quickButtons.addClassNames(LumoUtility.Gap.SMALL);
        quickButtons.setWidthFull();
        // Make buttons wrap on mobile
        quickButtons.getStyle().set("flex-wrap", "wrap");

        Button lastWeekBtn = new Button("Last 7 Days", e -> {
            setDateRange(7);
            calculateAverages();
        });
        Button lastMonthBtn = new Button("Last 30 Days", e -> {
            setDateRange(30);
            calculateAverages();
        });
        Button last3MonthsBtn = new Button("Last 90 Days", e -> {
            setDateRange(90);
            calculateAverages();
        });
        Button lastYearBtn = new Button("Last Year", e -> {
            setDateRange(365);
            calculateAverages();
        });
        Button currentYearBtn = new Button("Current Year", e -> {
            setCurrentYearRange();
            calculateAverages();
        });

        // Make buttons responsive - fit 2 per row on mobile
        lastWeekBtn.getStyle()
                .set("flex", "1 1 calc(50% - 0.25rem)")
                .set("min-width", "140px")
                .set("max-width", "100%");
        lastMonthBtn.getStyle()
                .set("flex", "1 1 calc(50% - 0.25rem)")
                .set("min-width", "140px")
                .set("max-width", "100%");
        last3MonthsBtn.getStyle()
                .set("flex", "1 1 calc(50% - 0.25rem)")
                .set("min-width", "140px")
                .set("max-width", "100%");
        lastYearBtn.getStyle()
                .set("flex", "1 1 calc(50% - 0.25rem)")
                .set("min-width", "140px")
                .set("max-width", "100%");
        currentYearBtn.getStyle()
                .set("flex", "1 1 calc(50% - 0.25rem)")
                .set("min-width", "140px")
                .set("max-width", "100%");

        quickButtons.add(lastWeekBtn, lastMonthBtn, last3MonthsBtn, lastYearBtn, currentYearBtn);

        Button calculateButton = new Button("Calculate Averages");
        calculateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        calculateButton.addClickListener(e -> calculateAverages());
        calculateButton.setWidthFull();

        section.add(form, quickButtons, calculateButton);
        return section;
    }

    private void setDateRange(int days) {
        toDatePicker.setValue(LocalDate.now().minusDays(1)); // Set to yesterday
        fromDatePicker.setValue(LocalDate.now().minusDays(days));
    }

    private void setCurrentYearRange() {
        fromDatePicker.setValue(LocalDate.of(LocalDate.now().getYear(), 1, 1)); // January 1st of current year
        toDatePicker.setValue(LocalDate.now().minusDays(1)); // Yesterday
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

        // Averages display
        if (result.getAverages() != null) {
            // Add heading with date range instead of separate box
            H3 heading = new H3("Averages for " + result.getFromDate() + " to " + result.getToDate());
            content.add(heading);

            // Use grid layout like dashboard for consistent mobile experience
            Div averagesGrid = new Div();
            averagesGrid.setWidthFull();
            averagesGrid.addClassNames(LumoUtility.Gap.MEDIUM);
            averagesGrid.addClassName("cards-grid");
            averagesGrid.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "repeat(auto-fit, minmax(120px, 1fr))")
                    .set("gap", "0.75rem");

            StatsDTO.Averages avg = result.getAverages();

            averagesGrid.add(
                    createAverageCard("Calories", formatNumber(avg.getAvgTotalCalories()), "kcal", "🔥"),
                    createAverageCard("Fat", formatNumber(avg.getAvgTotalFat()), "g", "🧈"),
                    createAverageCard("Carbs", formatNumber(avg.getAvgTotalCarbs()), "g", "🍞"),
                    createAverageCard("Sugar", formatNumber(avg.getAvgTotalSugar()), "g", "🍬"),
                    createAverageCard("Protein", formatNumber(avg.getAvgTotalProtein()), "g", "🥩"),
                    createAverageCard("Fibre", formatNumber(avg.getAvgTotalFibre()), "g", "🥦")
            );

            content.add(averagesGrid);

            // Visual comparison bars - Macro distribution
            content.add(new H3("Macro Distribution"));
            content.add(createMacroDistributionBars(avg));
        }

        resultDiv.add(content);
    }

    private Component createAverageCard(String nutrient, String value, String unit, String emoji) {
        Div card = new Div();
        card.addClassName("card");
        card.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.MEDIUM
        );
        // Match dashboard card styling exactly
        card.getStyle()
                .set("min-width", "100px")
                .set("max-width", "100%")
                .set("box-sizing", "border-box")
                .set("background-color", "rgba(78, 97, 155, 0.08)");

        Span emojiSpan = new Span(emoji);
        emojiSpan.addClassNames(LumoUtility.FontSize.XXLARGE);

        Span nutrientSpan = new Span(nutrient);
        nutrientSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD);

        Span valueSpan = new Span(value + " " + unit);
        valueSpan.addClassNames(LumoUtility.FontSize.LARGE);

        VerticalLayout layout = new VerticalLayout(emojiSpan, nutrientSpan, valueSpan);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setAlignItems(Alignment.CENTER);
        card.add(layout);
        return card;
    }

    private Component createMacroDistributionBars(StatsDTO.Averages avg) {
        VerticalLayout bars = new VerticalLayout();
        bars.addClassNames(
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.CONTRAST_5
        );
        bars.setSpacing(true);
        bars.setWidthFull();
        bars.setPadding(true);
        // Add minimal padding
        bars.getStyle()
                .set("padding", "1rem")
                .set("box-sizing", "border-box");

        // Calculate total macros
        double totalFat = avg.getAvgTotalFat();
        double totalCarbs = avg.getAvgTotalCarbs();
        double totalProtein = avg.getAvgTotalProtein();
        double totalMacros = totalFat + totalCarbs + totalProtein;

        if (totalMacros > 0) {
            // Calculate percentages
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

        // Top row: label and value
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

        // Bottom row: progress bar
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

    private String formatNumber(double value) {
        return String.format("%.1f", value);
    }

    private DatePicker.DatePickerI18n createDatePickerI18n() {
        DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
        i18n.setFirstDayOfWeek(1); // Monday
        i18n.setDateFormat("yyyy-MM-dd");
        return i18n;
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
