package dev.itobey.adapter.api.fddb.exporter.ui.components;

import com.vaadin.flow.component.HtmlContainer;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.formatNumber;

/**
 * A paged diary list using native disclosure rows and normal page scrolling.
 */
public class MobileEntriesList extends Div {
    private static final int PAGE_SIZE = 30;
    private static final String NEWEST_FIRST = "Newest first";
    private static final String OLDEST_FIRST = "Oldest first";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final Consumer<LocalDate> viewDetails;
    private final Select<String> sort = new Select<>();
    private final Div days = new Div();
    private final Span pageLabel = new Span();
    private final Button previous = new Button("Previous", e -> changePage(-1));
    private final Button next = new Button("Next", e -> changePage(1));
    private final Div pagination = new Div(previous, pageLabel, next);
    private List<FddbDataDTO> entries = List.of();
    private int page;

    public MobileEntriesList(Consumer<LocalDate> viewDetails) {
        this.viewDetails = viewDetails;
        addClassName("entries-mobile-list");
        setWidthFull();
        setVisible(false);
        sort.setLabel("Sort by date");
        sort.setItems(NEWEST_FIRST, OLDEST_FIRST);
        sort.setValue(NEWEST_FIRST);
        sort.addValueChangeListener(e -> {
            page = 0;
            render();
        });
        days.addClassName("entries-mobile-days");
        pagination.addClassName("entries-mobile-pagination");
        pageLabel.getElement().setAttribute("role", "status");
        pageLabel.getElement().setAttribute("aria-live", "polite");
        add(sort, days, pagination);
    }

    public void setItems(List<FddbDataDTO> items) {
        entries = items == null ? List.of() : List.copyOf(items);
        page = 0;
        setVisible(!entries.isEmpty());
        render();
    }

    private void changePage(int offset) {
        page += offset;
        render();
        getElement().executeJs("this.scrollIntoView({block: 'start'});");
    }

    private void render() {
        List<FddbDataDTO> sorted = new ArrayList<>(entries);
        Comparator<LocalDate> dates = OLDEST_FIRST.equals(sort.getValue())
                ? Comparator.naturalOrder() : Comparator.reverseOrder();
        sorted.sort(Comparator.comparing(FddbDataDTO::getDate, Comparator.nullsLast(dates)));
        days.removeAll();
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, sorted.size());
        YearMonth currentMonth = null;
        for (int i = start; i < end; i++) {
            FddbDataDTO day = sorted.get(i);
            YearMonth month = day.getDate() != null ? YearMonth.from(day.getDate()) : null;
            if (month != null && !month.equals(currentMonth)) {
                H3 heading = new H3(month.format(MONTH));
                heading.addClassName("entry-month-heading");
                days.add(heading);
                currentMonth = month;
            }
            days.add(createDay(day, i == start));
        }
        previous.setEnabled(page > 0);
        next.setEnabled(end < sorted.size());
        pageLabel.setText(sorted.isEmpty() ? "" : (start + 1) + "–" + end + " of " + sorted.size() + " days");
        pagination.setVisible(sorted.size() > PAGE_SIZE);
    }

    private HtmlContainer createDay(FddbDataDTO day, boolean opened) {
        LocalDate date = day.getDate();
        Span dateLabel = new Span(date != null ? date.format(DAY) : "Unknown date");
        dateLabel.addClassName("entry-day-date");
        int productCount = day.getProducts() != null ? day.getProducts().size() : 0;
        Span products = new Span(productCount + (productCount == 1 ? " product" : " products"));
        products.addClassName("entry-day-products");
        Span identity = new Span(dateLabel, products);
        identity.addClassName("entry-day-identity");
        Span calories = new Span(formatNumber(day.getTotalCalories()));
        calories.addClassName("entry-day-calories");
        Span unit = new Span("kcal");
        Span energy = new Span(calories, unit);
        energy.addClassName("entry-day-energy");
        HtmlContainer summary = new HtmlContainer("summary", identity, energy);
        summary.addClassName("entry-day-summary");

        HtmlContainer nutrients = new HtmlContainer("dl");
        nutrients.addClassName("entry-day-nutrients");
        nutrients.add(nutrient("Protein", day.getTotalProtein()), nutrient("Carbs", day.getTotalCarbs()),
                nutrient("Fat", day.getTotalFat()), nutrient("Sugar", day.getTotalSugar()),
                nutrient("Fibre", day.getTotalFibre()));
        Button details = new Button("View Details", e -> viewDetails.accept(date));
        details.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        details.addClassName("entry-day-details");
        details.setAriaLabel("View details for " + date);
        details.setEnabled(date != null);
        HtmlContainer row = new HtmlContainer("details", summary, nutrients, details);
        row.addClassName("entry-day");
        row.getElement().setAttribute("open", opened);
        return row;
    }

    private Div nutrient(String name, double amount) {
        HtmlContainer label = new HtmlContainer("dt");
        label.setText(name);
        HtmlContainer value = new HtmlContainer("dd");
        value.setText(formatNumber(amount) + " g");
        Div nutrient = new Div(label, value);
        nutrient.addClassName("entry-nutrient--" + name.toLowerCase(Locale.ROOT));
        return nutrient;
    }
}
