package dev.itobey.adapter.api.fddb.exporter.ui.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.formatNumber;

/**
 * Period bars share one keyboard tab stop; arrow keys, Home and End inspect every period.
 */
public class TrendChart extends Div {
    private static final DateTimeFormatter TICK_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private final List<NativeButton> bars = new ArrayList<>();
    private final Paragraph detail = new Paragraph();
    private final List<TrendPointDTO> points;
    private final TrendGranularity granularity;
    private final String unit;

    public TrendChart(List<TrendPointDTO> points, TrendGranularity granularity, String unit, String fill) {
        this.points = List.copyOf(points);
        this.granularity = granularity;
        this.unit = unit;
        addClassName("trend-chart");
        setWidthFull();

        double ceiling = scaleCeiling(points.stream().mapToDouble(TrendPointDTO::getAverage).max().orElse(0));
        long days = points.stream().mapToLong(TrendPointDTO::getDayCount).sum();
        double average = days > 0 ? points.stream().mapToDouble(TrendPointDTO::getTotal).sum() / days : 0;
        Div scale = new Div(new Span(formatNumber(ceiling)), new Span(formatNumber(ceiling / 2)), new Span("0"));
        scale.addClassName("trend-value-axis");
        scale.getElement().setAttribute("aria-hidden", "true");
        Div plot = new Div();
        plot.addClassName("trend-plot");
        plot.getStyle().set("min-width", points.size() * 56 + "px");
        plot.getElement().setAttribute("role", "group");
        plot.getElement().setAttribute("aria-label", "Daily average in " + unit + " by period");
        Div ticks = new Div();
        ticks.addClassName("trend-date-axis");
        ticks.getStyle().set("min-width", points.size() * 56 + "px")
                .set("grid-template-columns", "repeat(" + points.size() + ", minmax(0, 1fr))");
        int stride = Math.max(1, (int) Math.ceil(points.size() / 8.0));
        for (int i = 0; i < points.size(); i++) {
            int index = i;
            TrendPointDTO point = points.get(i);
            NativeButton button = new NativeButton();
            button.addClassName("trend-period");
            button.getElement().setAttribute("type", "button");
            button.getElement().setAttribute("aria-label", describe(point, granularity, unit));
            Div bar = new Div();
            bar.addClassName("trend-period__bar");
            bar.getStyle().set("height", Math.max(0, point.getAverage() / ceiling * 100) + "%")
                    .set("background", fill);
            bar.getElement().setAttribute("aria-hidden", "true");
            button.add(bar);
            if (isIncomplete(point, granularity)) {
                button.addClassName("trend-period--incomplete");
            }
            button.addClickListener(e -> select(index, false));
            button.getElement().addEventListener("focus", e -> select(index, false));
            bindKey(button, "ArrowLeft", () -> select(Math.max(0, index - 1), true));
            bindKey(button, "ArrowRight", () -> select(Math.min(points.size() - 1, index + 1), true));
            bindKey(button, "Home", () -> select(0, true));
            bindKey(button, "End", () -> select(points.size() - 1, true));
            bars.add(button);
            plot.add(button);
            Span tick = new Span(i % stride == 0 || i == points.size() - 1
                    ? point.getFromDate().format(TICK_DATE) : "");
            ticks.add(tick);
        }
        Div averageLine = new Div();
        averageLine.addClassName("trend-average-line");
        averageLine.getStyle().set("bottom", average / ceiling * 100 + "%");
        averageLine.getElement().setAttribute("aria-hidden", "true");
        plot.add(averageLine);
        Div scroller = new Div(plot, ticks);
        scroller.addClassName("trend-chart-scroller");
        Div frame = new Div(scale, scroller);
        frame.addClassName("trend-chart-frame");
        Paragraph instructions = new Paragraph("Tap a bar to inspect it. Use ← / →, Home or End with the keyboard. Scroll sideways for more periods.");
        instructions.addClassName("trend-chart-help");
        Span legend = new Span("Daily average (" + unit + ") · Dashed line: " + formatNumber(average)
                + " " + unit + " per logged day · Dotted bars: incomplete coverage");
        legend.addClassName("trend-chart-legend");
        detail.addClassName("trend-period-detail");
        detail.getElement().setAttribute("role", "status");
        detail.getElement().setAttribute("aria-live", "polite");
        add(legend, instructions, frame, detail);
        select(points.size() - 1, false);
    }

    private void select(int index, boolean focus) {
        for (int i = 0; i < bars.size(); i++) {
            bars.get(i).getElement().setAttribute("tabindex", i == index ? "0" : "-1");
            bars.get(i).getElement().setAttribute("aria-pressed", String.valueOf(i == index));
        }
        detail.setText(describe(points.get(index), granularity, unit));
        if (focus) {
            bars.get(index).getElement().executeJs("this.focus(); this.scrollIntoView({block:'nearest', inline:'nearest'});");
        }
    }

    private void bindKey(NativeButton button, String key, Runnable action) {
        button.getElement().addEventListener("keydown", e -> action.run())
                .setFilter("event.key === '" + key + "'").preventDefault();
    }

    public static int expectedDays(TrendPointDTO point, TrendGranularity granularity) {
        return switch (granularity) {
            case DAY -> 1;
            case WEEK -> 7;
            case MONTH -> point.getFromDate().lengthOfMonth();
        };
    }

    public static boolean isIncomplete(TrendPointDTO point, TrendGranularity granularity) {
        return point.getDayCount() < expectedDays(point, granularity);
    }

    public static String coverage(TrendPointDTO point, TrendGranularity granularity) {
        return point.getDayCount() + " of " + expectedDays(point, granularity) + " days logged";
    }

    public static String describe(TrendPointDTO point, TrendGranularity granularity, String unit) {
        return point.getBucket() + " · " + point.getFromDate() + " to " + point.getToDate()
                + " · " + formatNumber(point.getAverage()) + " " + unit + " per logged day · "
                + coverage(point, granularity) + (isIncomplete(point, granularity) ? " · Incomplete coverage" : "");
    }

    static double scaleCeiling(double max) {
        if (max <= 0) return 1;
        double step = Math.pow(10, Math.floor(Math.log10(max))) / 2;
        return Math.ceil(max / step) * step;
    }
}
