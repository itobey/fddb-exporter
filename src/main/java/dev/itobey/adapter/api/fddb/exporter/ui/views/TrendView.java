package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
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
    private static final String HIGHLIGHT_COLOR = "#ae9357";
    private static final String CHART_HEIGHT = "260px";
    private static final int INLINE_GRID_ROWS = 15;

    private final StatsClient statsClient;

    private ComboBox<NutrientMetric> metricSelect;
    private ComboBox<TrendGranularity> granularitySelect;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private Div resultDiv;

    public TrendView(StatsClient statsClient, FddbExporterProperties properties) {
        this.statsClient = statsClient;

        addClassName("trend-view");
        setSpacing(true);
        setPadding(true);
        applyResponsivePadding(this);

        add(new H2("Trends"));
        add(new Paragraph("Follow a single nutritional metric over time, bucketed by day, week or month. "
                + "Buckets without a single entry are omitted, so unlogged days never drag an average down."));

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

        granularitySelect = new ComboBox<>("Granularity");
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

        section.add(metricRow, dateRow, quickButtons, loadButton);
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

    private void loadTrend() {
        if (fromDatePicker.getValue() == null || toDatePicker.getValue() == null) {
            showError("Please select both from and to dates");
            return;
        }

        if (fromDatePicker.getValue().isAfter(toDatePicker.getValue())) {
            showError("From date must be before or equal to to date");
            return;
        }

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
            resultDiv.getElement().executeJs("this.scrollIntoView({behavior: 'smooth', block: 'start'})");

            if (points == null || points.isEmpty()) {
                showError("No entries found for the selected range");
            } else {
                showSuccess("Trend loaded successfully");
            }
        } catch (ApiException apiException) {
            showError(apiException.getMessage());
        }
    }

    private void displayResult(List<TrendPointDTO> points, NutrientMetric metric, TrendGranularity granularity) {
        resultDiv.removeAll();
        resultDiv.setVisible(true);

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);

        if (points == null || points.isEmpty()) {
            Paragraph noData = new Paragraph("No entries found for the selected range.");
            noData.addClassNames(LumoUtility.TextColor.SECONDARY);
            content.add(noData);
            resultDiv.add(content);
            return;
        }

        content.add(new H3(metricLabel(metric) + " per " + bucketNoun(granularity)));
        content.add(createSummaryCards(points, metric));
        content.add(createChart(points, metric));
        content.add(createTrendGrid(points, metric));

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
                createStatCard("Buckets", String.valueOf(points.size()), "with at least one entry"),
                createStatCard("Days logged", String.valueOf(totalDays), "across all buckets"),
                createStatCard("Daily average", formatNumber(overallAverage) + " " + unit, "per logged day"),
                createStatCard("Highest", formatNumber(highest.getAverage()) + " " + unit, highest.getBucket()),
                createStatCard("Lowest", formatNumber(lowest.getAverage()) + " " + unit, lowest.getBucket()),
                createStatCard("Change", formatChange(change, unit), first.getBucket() + " ⮕ " + last.getBucket())
        );
        return cards;
    }

    private String formatChange(double change, String unit) {
        String arrow = change > 0 ? "▲ +" : (change < 0 ? "▼ " : "");
        return arrow + formatNumber(change) + " " + unit;
    }

    /**
     * Renders the time series as a CSS column chart - the project ships no charting library,
     * so the bars are plain divs sized relative to the highest bucket.
     */
    private Component createChart(List<TrendPointDTO> points, NutrientMetric metric) {
        String unit = unitOf(metric);

        double max = points.stream().mapToDouble(TrendPointDTO::getAverage).max().orElse(0);
        long totalDays = points.stream().mapToLong(TrendPointDTO::getDayCount).sum();
        double totalValue = points.stream().mapToDouble(TrendPointDTO::getTotal).sum();
        double average = totalDays > 0 ? totalValue / totalDays : 0;

        VerticalLayout container = new VerticalLayout();
        container.addClassName("trend-chart");
        container.addClassNames(LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);
        container.setWidthFull();
        container.setPadding(true);
        container.setSpacing(false);
        container.getStyle().set("padding", "1rem").set("box-sizing", "border-box");

        Span maxLabel = new Span("Peak " + formatNumber(max) + " " + unit);
        maxLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        container.add(maxLabel);

        Div plotArea = new Div();
        plotArea.setWidthFull();
        plotArea.setHeight(CHART_HEIGHT);
        plotArea.getStyle()
                .set("position", "relative")
                .set("display", "flex")
                .set("flex-direction", "row")
                .set("align-items", "flex-end")
                .set("gap", points.size() > 60 ? "1px" : "3px")
                .set("border-bottom", "1px solid var(--lumo-contrast-20pct)")
                .set("margin-top", "0.5rem")
                .set("box-sizing", "border-box");

        for (TrendPointDTO point : points) {
            plotArea.add(createBar(point, max, unit));
        }

        if (max > 0) {
            plotArea.add(createAverageLine(average, max));
        }

        container.add(plotArea);

        Div axis = new Div();
        axis.setWidthFull();
        axis.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("margin-top", "0.25rem");
        axis.add(axisLabel(points.getFirst().getBucket()), axisLabel(points.getLast().getBucket()));
        container.add(axis);

        Span legend = new Span("┈ Average " + formatNumber(average) + " " + unit + " per logged day");
        legend.addClassNames(LumoUtility.FontSize.SMALL);
        legend.getStyle().set("color", HIGHLIGHT_COLOR).set("margin-top", "0.5rem");
        container.add(legend);

        return container;
    }

    private Div createBar(TrendPointDTO point, double max, String unit) {
        Div column = new Div();
        column.getStyle()
                .set("flex", "1 1 0")
                .set("min-width", "2px")
                .set("height", "100%")
                .set("display", "flex")
                .set("align-items", "flex-end");

        double heightPercentage = max > 0 ? (point.getAverage() / max) * 100 : 0;

        Div bar = new Div();
        bar.setWidthFull();
        bar.getStyle()
                .set("height", Math.max(heightPercentage, 1) + "%")
                .set("background-color", "var(--accent)")
                .set("border-radius", "2px 2px 0 0");
        bar.getElement().setAttribute("title", point.getBucket()
                + ": " + formatNumber(point.getAverage()) + " " + unit
                + " (" + point.getDayCount() + (point.getDayCount() == 1 ? " day" : " days") + ")");

        column.add(bar);
        return column;
    }

    private Div createAverageLine(double average, double max) {
        Div line = new Div();
        line.getStyle()
                .set("position", "absolute")
                .set("left", "0")
                .set("right", "0")
                .set("bottom", ((average / max) * 100) + "%")
                .set("border-top", "1px dashed " + HIGHLIGHT_COLOR)
                .set("pointer-events", "none");
        return line;
    }

    private Span axisLabel(String text) {
        Span label = new Span(text);
        label.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        return label;
    }

    private Component createTrendGrid(List<TrendPointDTO> points, NutrientMetric metric) {
        String unit = unitOf(metric);

        Grid<TrendPointDTO> grid = new Grid<>(TrendPointDTO.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(TrendPointDTO::getBucket).setHeader("Bucket").setAutoWidth(true);
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
        String name = metric.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
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
}
