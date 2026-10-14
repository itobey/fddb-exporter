package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
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
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.components.TrendChart;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.StatsClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

/**
 * Visualizes the {@code /api/v2/stats/trend} time series: one metric over a date range,
 * bucketed by day, ISO week or month.
 */
@Route(value = "trend", layout = MainLayout.class)
@PageTitle("Trends | FDDB Exporter")
public class TrendView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String HIGHLIGHT_TEXT = "var(--highlight-text)";
    private static final int INLINE_GRID_ROWS = 15;

    private final StatsClient statsClient;

    private ComboBox<NutrientMetric> metricSelect;
    private ComboBox<TrendGranularity> granularitySelect;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private Div resultDiv;
    private Details customRange;
    private final Div feedback = createFeedbackRegion();

    public TrendView(StatsClient statsClient, FddbExporterProperties properties) {
        this.statsClient = statsClient;

        addClassName("trend-view");
        setSpacing(true);
        setPadding(true);
        applyResponsivePadding(this);

        add(new H2("Trends"));
        add(new Paragraph("Follow a nutritional metric by day, week or month. "
                + "Averages use logged days only. Periods with no entries are omitted."));

        if (!isMongoDbEnabled(properties)) {
            add(createMongoDbDisabledWarning("Trends"));
            return;
        }

        add(createInputForm());
        add(createResultSection());
    }

    private VerticalLayout createInputForm() {
        VerticalLayout section = createSection(null);
        section.add(new H3("Select Metric and Range"));

        metricSelect = new ComboBox<>("Metric");
        metricSelect.setItems(NutrientMetric.values());
        metricSelect.setItemLabelGenerator(TrendView::metricLabel);
        metricSelect.setValue(NutrientMetric.CALORIES);
        metricSelect.setAllowCustomValue(false);
        metricSelect.setWidthFull();

        granularitySelect = new ComboBox<>("Group by");
        granularitySelect.setItems(TrendGranularity.values());
        granularitySelect.setItemLabelGenerator(TrendView::granularityLabel);
        granularitySelect.setValue(TrendGranularity.WEEK);
        granularitySelect.setAllowCustomValue(false);
        granularitySelect.setWidthFull();

        fromDatePicker = new DatePicker("From Date");
        fromDatePicker.setValue(LocalDate.now().minusMonths(3));
        fromDatePicker.setRequired(true);
        fromDatePicker.setI18n(createDatePickerI18n());
        fromDatePicker.setWidthFull();

        toDatePicker = new DatePicker("To Date");
        toDatePicker.setValue(LocalDate.now().minusDays(1));
        toDatePicker.setRequired(true);
        toDatePicker.setI18n(createDatePickerI18n());
        toDatePicker.setWidthFull();

        HorizontalLayout metricRow = new HorizontalLayout(metricSelect, granularitySelect);
        metricRow.addClassName("query-filter-row");
        metricRow.setWidthFull();
        metricRow.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout dateRow = new HorizontalLayout(fromDatePicker, toDatePicker);
        dateRow.addClassName("query-filter-row");
        dateRow.setWidthFull();
        dateRow.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout quickButtons = new HorizontalLayout();
        quickButtons.addClassName("preset-buttons-container");
        quickButtons.addClassNames(LumoUtility.Gap.SMALL);
        quickButtons.setWidthFull();
        quickButtons.getStyle().set("flex-wrap", "wrap");
        quickButtons.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        quickButtons.add(
                createPresetButton("Last 30 Days", TrendGranularity.DAY, 30),
                createPresetButton("Last 90 Days", TrendGranularity.WEEK, 90),
                createPresetButton("Last Year", TrendGranularity.WEEK, 365),
                createCurrentYearButton()
        );

        Button loadButton = new Button("Show Trend");
        loadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loadButton.addClickListener(e -> loadTrend());
        loadButton.setWidthFull();

        customRange = new Details("Custom date range", dateRow);
        customRange.setWidthFull();
        customRange.addClassName("trend-custom-range");
        Span rangeSummary = new Span();
        rangeSummary.addClassName("trend-range-summary");
        Runnable updateSummary = () -> rangeSummary.setText(
                fromDatePicker.getValue() != null && toDatePicker.getValue() != null
                        ? fromDatePicker.getValue() + " → " + toDatePicker.getValue()
                        : "Select both dates in Custom date range");
        fromDatePicker.addValueChangeListener(e -> updateSummary.run());
        toDatePicker.addValueChangeListener(e -> updateSummary.run());
        updateSummary.run();
        section.add(metricRow, quickButtons, rangeSummary, loadButton, customRange, feedback);
        return section;
    }

    private Button createPresetButton(String label, TrendGranularity granularity, int days) {
        Button button = new Button(label, e -> {
            granularitySelect.setValue(granularity);
            toDatePicker.setValue(LocalDate.now().minusDays(1));
            fromDatePicker.setValue(LocalDate.now().minusDays(days));
            loadTrend();
        });
        return styleAsPreset(button);
    }

    private Button createCurrentYearButton() {
        Button button = new Button("Current Year", e -> {
            granularitySelect.setValue(TrendGranularity.MONTH);
            fromDatePicker.setValue(LocalDate.of(LocalDate.now().getYear(), 1, 1));
            toDatePicker.setValue(LocalDate.now().minusDays(1));
            loadTrend();
        });
        return styleAsPreset(button);
    }

    private Button styleAsPreset(Button button) {
        button.addThemeVariants(ButtonVariant.LUMO_SMALL);
        button.addClassName("preset-btn");
        button.getStyle()
                .set("flex", "1 1 calc(50% - 0.25rem)")
                .set("min-width", "calc(50% - 0.25rem)")
                .set("color", HIGHLIGHT_TEXT);
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

    private void loadTrend() {
        if (!validateDateRange(fromDatePicker, toDatePicker, false)) {
            customRange.setOpened(true);
            return;
        }
        feedback.setVisible(false);

        NutrientMetric metric = metricSelect.getValue() != null ? metricSelect.getValue() : NutrientMetric.CALORIES;
        TrendGranularity granularity = granularitySelect.getValue() != null
                ? granularitySelect.getValue() : TrendGranularity.DAY;

        try {
            List<TrendPointDTO> points = statsClient.getTrend(
                    metric,
                    fromDatePicker.getValue().format(DATE_FORMAT),
                    toDatePicker.getValue().format(DATE_FORMAT),
                    granularity);

            displayResult(points, metric, granularity);
            scrollIntoViewWhenSettled(resultDiv);

            if (points != null && !points.isEmpty()) {
                showSuccess("Trend loaded successfully");
            }
        } catch (ApiException apiException) {
            resultDiv.setVisible(false);
            showInlineError(feedback, apiException.getMessage(), this::loadTrend);
        }
    }

    private void displayResult(List<TrendPointDTO> points, NutrientMetric metric, TrendGranularity granularity) {
        resultDiv.removeAll();
        resultDiv.setVisible(true);

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);

        if (points == null || points.isEmpty()) {
            Paragraph noData = new Paragraph("No entries found from " + fromDatePicker.getValue()
                    + " to " + toDatePicker.getValue() + ". Choose another preset or widen the custom date range.");
            noData.addClassNames(LumoUtility.TextColor.SECONDARY);
            content.add(noData);
            resultDiv.add(content);
            return;
        }

        content.add(new H3(metricLabel(metric) + " per " + bucketNoun(granularity)));
        content.add(createSummaryCards(points, metric));
        Paragraph comparison = new Paragraph(points.size() < 2
                ? "Change needs at least two periods with entries."
                : "Change compares the last period's daily average with the first: "
                + points.getFirst().getBucket() + " (" + TrendChart.coverage(points.getFirst(), granularity)
                + ") → " + points.getLast().getBucket() + " (" + TrendChart.coverage(points.getLast(), granularity)
                + "). Incomplete coverage may reflect the selected range or missing diary days.");
        comparison.addClassName("trend-comparison-note");
        content.add(comparison);
        content.add(createChart(points, metric, granularity));
        content.add(createTrendGrid(points, metric, granularity));

        resultDiv.add(content);
    }

    private Component createSummaryCards(List<TrendPointDTO> points, NutrientMetric metric) {
        String unit = unitOf(metric);

        long totalDays = points.stream().mapToLong(TrendPointDTO::getDayCount).sum();
        double totalValue = points.stream().mapToDouble(TrendPointDTO::getTotal).sum();
        double overallAverage = totalDays > 0 ? totalValue / totalDays : 0;

        TrendPointDTO highest = points.stream().max(java.util.Comparator.comparingDouble(TrendPointDTO::getAverage))
                .orElseThrow();
        TrendPointDTO lowest = points.stream().min(java.util.Comparator.comparingDouble(TrendPointDTO::getAverage))
                .orElseThrow();

        TrendPointDTO first = points.getFirst();
        TrendPointDTO last = points.getLast();
        double change = last.getAverage() - first.getAverage();

        Div cards = createCardsGrid("140px");
        cards.add(
                createStatCard("Periods", String.valueOf(points.size()), "with at least one entry"),
                createStatCard("Days logged", String.valueOf(totalDays), "across all periods"),
                createStatCard("Daily average", formatNumber(overallAverage) + " " + unit, "per logged day"),
                createStatCard("Highest", formatNumber(highest.getAverage()) + " " + unit, highest.getBucket()),
                createStatCard("Lowest", formatNumber(lowest.getAverage()) + " " + unit, lowest.getBucket()),
                createStatCard("Change", points.size() > 1 ? formatChange(change, unit) : "N/A",
                        points.size() > 1 ? "Last vs first daily average" : "Only one period")
        );
        return cards;
    }

    private String formatChange(double change, String unit) {
        String arrow = change > 0 ? "▲ +" : (change < 0 ? "▼ " : "");
        return arrow + formatNumber(change) + " " + unit;
    }

    /**
     * The chart owns scales, period inspection and coverage markers.
     */
    private Component createChart(List<TrendPointDTO> points, NutrientMetric metric, TrendGranularity granularity) {
        return new TrendChart(points, granularity, unitOf(metric), fillOf(metric));
    }

    private Component createTrendGrid(List<TrendPointDTO> points, NutrientMetric metric, TrendGranularity granularity) {
        String unit = unitOf(metric);

        Grid<TrendPointDTO> grid = new Grid<>(TrendPointDTO.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(TrendPointDTO::getBucket).setHeader("Period").setAutoWidth(true);
        grid.addColumn(point -> TrendChart.isIncomplete(point, granularity)
                        ? "Incomplete: " + TrendChart.coverage(point, granularity) : "Complete")
                .setHeader("Coverage").setAutoWidth(true);
        grid.addColumn(TrendPointDTO::getFromDate).setHeader("From").setAutoWidth(true);
        grid.addColumn(TrendPointDTO::getToDate).setHeader("To").setAutoWidth(true);
        grid.addColumn(TrendPointDTO::getDayCount).setHeader("Days").setAutoWidth(true);
        grid.addColumn(point -> formatNumber(point.getAverage()))
                .setHeader("Average (" + unit + ")").setAutoWidth(true);
        grid.addColumn(point -> formatNumber(point.getTotal()))
                .setHeader("Total (" + unit + ")").setAutoWidth(true);

        grid.setItems(points);
        if (points.size() > INLINE_GRID_ROWS) {
            grid.setHeight("400px");
        } else {
            grid.setAllRowsVisible(true);
        }
        return grid;
    }

    private static String metricLabel(NutrientMetric metric) {
        return capitalize(metric.name());
    }

    private static String granularityLabel(TrendGranularity granularity) {
        return switch (granularity) {
            case DAY -> "Daily";
            case WEEK -> "Weekly (ISO)";
            case MONTH -> "Monthly";
        };
    }

    private static String bucketNoun(TrendGranularity granularity) {
        return switch (granularity) {
            case DAY -> "day";
            case WEEK -> "week";
            case MONTH -> "month";
        };
    }

    private static String unitOf(NutrientMetric metric) {
        return metric == NutrientMetric.CALORIES ? "kcal" : "g";
    }

    /**
     * The nutrient identity fill of the charted metric - a bar is a mark, never text,
     * so this consumes the fill token rather than its {@code -text} counterpart.
     */
    private static String fillOf(NutrientMetric metric) {
        return switch (metric) {
            case CALORIES -> "var(--nutrient-calories)";
            case FAT -> "var(--nutrient-fat)";
            case CARBS -> "var(--nutrient-carbs)";
            case SUGAR -> "var(--nutrient-sugar)";
            case PROTEIN -> "var(--nutrient-protein)";
            case FIBRE -> "var(--nutrient-fibre)";
        };
    }
}
