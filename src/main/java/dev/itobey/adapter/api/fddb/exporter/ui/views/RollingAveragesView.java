package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.domain.RollingAveragePreset;
import dev.itobey.adapter.api.fddb.exporter.dto.MacroSplitDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.WeekdayStatsDTO;
import dev.itobey.adapter.api.fddb.exporter.service.UserSettingsService;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.StatsClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

@Route(value = "averages", layout = MainLayout.class)
@PageTitle("Rolling Averages | FDDB Exporter")
public class RollingAveragesView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** Below these shares of the bar a segment is too narrow to hold the text; the legend takes over. */
    private static final double NAME_MIN_PERCENT = 15;
    private static final double VALUE_MIN_PERCENT = 8;
    private static final String BG_COLOR = "var(--accent-surface)";
    // Only ever used as a text color in this view.
    private static final String HIGHLIGHT_COLOR = "var(--highlight-text)";

    private final StatsClient statsClient;
    private final FddbExporterProperties properties;
    private final UserSettingsService userSettingsService;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private Div resultDiv;

    public RollingAveragesView(StatsClient statsClient, FddbExporterProperties properties,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) UserSettingsService userSettingsService) {
        this.statsClient = statsClient;
        this.properties = properties;
        this.userSettingsService = userSettingsService;

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

        List<RollingAveragePreset> customPresets = userSettingsService != null
                ? userSettingsService.getSettings().getRollingAveragePresets()
                : java.util.Collections.emptyList();
        for (RollingAveragePreset preset : customPresets) {
            quickButtons.add(createCustomPresetButton(preset));
        }

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

    private Button createCustomPresetButton(RollingAveragePreset preset) {
        Button button = new Button(preset.getName(), e -> {
            fromDatePicker.setValue(preset.getFromDate());
            toDatePicker.setValue(preset.getToDate());
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
            MacroSplitDTO macroSplit = statsClient.getMacroSplit(fromDate, toDate);
            List<WeekdayStatsDTO> weekdayBreakdown = statsClient.getWeekdayBreakdown(fromDate, toDate);
            displayResult(result, macroSplit, weekdayBreakdown);
            // prefers-reduced-motion cannot be applied to a script-initiated scroll from CSS, so the
            // theme's Reduced Motion Rule has to be honoured here explicitly.
            resultDiv.getElement().executeJs("this.scrollIntoView({"
                    + "behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches"
                    + " ? 'auto' : 'smooth', block: 'start'})");
            showSuccess("Averages calculated successfully");
        } catch (ApiException apiException) {
            showError(apiException.getMessage());
        }
    }

    private void displayResult(RollingAveragesDTO result, MacroSplitDTO macroSplit,
                               List<WeekdayStatsDTO> weekdayBreakdown) {
        resultDiv.removeAll();
        resultDiv.setVisible(true);

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);

        if (result.getAverages() != null) {
            H3 heading = new H3(result.getFromDate() + " ⮕ " + result.getToDate());
            content.add(heading);

            long dayCount = ChronoUnit.DAYS.between(
                    LocalDate.parse(result.getFromDate(), DATE_FORMAT),
                    LocalDate.parse(result.getToDate(), DATE_FORMAT)) + 1;
            Paragraph dayCountLabel = new Paragraph(dayCount + (dayCount == 1 ? " day" : " days"));
            dayCountLabel.addClassNames(LumoUtility.TextColor.SECONDARY);
            dayCountLabel.getStyle().set("margin-top", "0").set("margin-bottom", "0.25rem");
            content.add(dayCountLabel);

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
            content.add(new Paragraph("Share of energy per macro, kcal-weighted (fat 9 kcal/g, carbs and protein 4 kcal/g)."));
            content.add(createMacroDistributionBars(macroSplit));
        }

        if (weekdayBreakdown != null && !weekdayBreakdown.isEmpty()) {
            content.add(new H3("By Day of the Week"));
            content.add(createWeekdayGrid(weekdayBreakdown));
        }

        resultDiv.add(content);
    }

    private Component createWeekdayGrid(List<WeekdayStatsDTO> weekdayBreakdown) {
        Grid<WeekdayStatsDTO> grid = new Grid<>(WeekdayStatsDTO.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setAllRowsVisible(true);

        grid.addColumn(stats -> capitalize(stats.getDayOfWeek().name())).setHeader("Day").setAutoWidth(true);
        grid.addColumn(WeekdayStatsDTO::getDayCount).setHeader("Entries").setAutoWidth(true);
        grid.addColumn(stats -> formatNumber(stats.getAverages().getAvgTotalCalories())).setHeader("Calories").setAutoWidth(true);
        grid.addColumn(stats -> formatNumber(stats.getAverages().getAvgTotalFat())).setHeader("Fat (g)").setAutoWidth(true);
        grid.addColumn(stats -> formatNumber(stats.getAverages().getAvgTotalCarbs())).setHeader("Carbs (g)").setAutoWidth(true);
        grid.addColumn(stats -> formatNumber(stats.getAverages().getAvgTotalSugar())).setHeader("Sugar (g)").setAutoWidth(true);
        grid.addColumn(stats -> formatNumber(stats.getAverages().getAvgTotalProtein())).setHeader("Protein (g)").setAutoWidth(true);
        grid.addColumn(stats -> formatNumber(stats.getAverages().getAvgTotalFibre())).setHeader("Fibre (g)").setAutoWidth(true);

        grid.setItems(weekdayBreakdown);
        return grid;
    }

    private String capitalize(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private Component createMacroDistributionBars(MacroSplitDTO macroSplit) {
        VerticalLayout container = new VerticalLayout();
        container.addClassNames(LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);
        container.setSpacing(true);
        container.setWidthFull();
        container.setPadding(true);
        container.getStyle().set("padding", "1rem").set("box-sizing", "border-box");

        if (macroSplit == null || macroSplit.getMacroCalories() <= 0) {
            Paragraph noData = new Paragraph("No macro data available");
            noData.addClassNames(LumoUtility.TextColor.SECONDARY);
            container.add(noData);
            return container;
        }

        Div stackedBar = new Div();
        stackedBar.addClassName("macro-bar");

        // The macro split uses the nutrient identity colors, so a macro reads the same here as it
        // does in a card, a chart or a table. Because the palette is lightness-matched, one ink is
        // readable on all three fills (7.4:1, 8.0:1, 7.4:1) - the ink still travels with each
        // segment rather than being assumed, so a future palette change surfaces here.
        List<MacroSegment> segments = List.of(
                new MacroSegment("Fat", macroSplit.getFatPercentage(), "var(--nutrient-fat)"),
                new MacroSegment("Carbs", macroSplit.getCarbsPercentage(), "var(--nutrient-carbs)"),
                new MacroSegment("Protein", macroSplit.getProteinPercentage(), "var(--nutrient-protein)")
        );

        segments.forEach(segment -> stackedBar.add(createMacroSection(segment)));
        container.add(stackedBar);

        // Anything too narrow to name itself is named underneath instead, so a low-protein day
        // still reports its protein rather than showing a sliver with the label clipped off.
        List<MacroSegment> unnamed = segments.stream()
                .filter(segment -> segment.percentage() < NAME_MIN_PERCENT)
                .toList();
        if (!unnamed.isEmpty()) {
            container.add(createMacroLegend(unnamed));
        }
        return container;
    }

    private record MacroSegment(String name, double percentage, String fill) {
    }

    private Div createMacroSection(MacroSegment segment) {
        Div section = new Div();
        section.addClassName("macro-bar__segment");
        section.getStyle()
                .set("flex", "0 0 " + segment.percentage() + "%")
                .set("background-color", segment.fill());

        // Below the thresholds the text cannot fit the segment at any realistic bar width, so it
        // is dropped here rather than clipped, and the legend picks it up.
        if (segment.percentage() >= NAME_MIN_PERCENT) {
            section.add(createMacroLabel(segment.name()));
        }
        if (segment.percentage() >= VALUE_MIN_PERCENT) {
            section.add(createMacroLabel(formatNumber(segment.percentage()) + "%"));
        }
        // A segment with no room for a label still needs a name for assistive technology.
        section.getElement().setAttribute("aria-label",
                segment.name() + " " + formatNumber(segment.percentage()) + "%");
        return section;
    }

    private Span createMacroLabel(String text) {
        Span label = new Span(text);
        label.addClassName("macro-bar__label");
        // The ink travels with the segment rather than being assumed globally.
        label.getStyle().set("color", "var(--button-color-contrast)");
        return label;
    }

    private Div createMacroLegend(List<MacroSegment> segments) {
        Div legend = new Div();
        legend.addClassName("macro-legend");
        for (MacroSegment segment : segments) {
            Span swatch = new Span();
            swatch.addClassName("macro-legend__swatch");
            swatch.getStyle().set("background-color", segment.fill());

            Span value = new Span(formatNumber(segment.percentage()) + "%");
            value.addClassName("macro-legend__value");

            Div item = new Div(swatch, new Span(segment.name()), value);
            item.addClassName("macro-legend__item");
            legend.add(item);
        }
        return legend;
    }
}
