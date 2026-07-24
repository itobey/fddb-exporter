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
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.FddbDataClient;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

/**
 * Product-centric view. The <em>Explorer</em> tab resolves a fuzzy product name to the exact,
 * brand-prefixed names FDDB stores (via {@code /products/distinct}), then shows an aggregated
 * profile of the matching products ({@code /products/summary}) alongside the individual
 * occurrences ({@code /products}). The <em>Top Products</em> tab ranks products by frequency or by
 * a nutrient total ({@code /products/top}).
 */
@Route(value = "products", layout = MainLayout.class)
@PageTitle("Products | FDDB Exporter")
public class ProductsView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int DISTINCT_SUGGESTION_LIMIT = 50;
    private static final int OCCURRENCE_LIMIT = 500;
    private static final int DEFAULT_TOP_PRODUCTS_LIMIT = 20;
    private static final int MAX_TOP_PRODUCTS_LIMIT = 500;
    private static final String BAR_COLOR = "#ae9357";

    private final FddbDataClient fddbDataClient;
    private final String fddbLinkPrefix;

    private final TabSheet tabSheet = new TabSheet();

    private ComboBox<String> productNameCombo;
    private DatePicker explorerFromDatePicker;
    private DatePicker explorerToDatePicker;
    private Div summarySection;
    private Grid<ProductWithDateDTO> occurrencesGrid;
    private Span occurrencesCountLabel;

    // Explorer weekday filter: the full occurrence set for the current term is held in memory and
    // filtered client-side when weekday distribution bars are toggled, so no refetch is needed.
    private final Set<DayOfWeek> selectedWeekdays = EnumSet.noneOf(DayOfWeek.class);
    private final Map<DayOfWeek, Div> weekdayBars = new EnumMap<>(DayOfWeek.class);
    private List<ProductWithDateDTO> allOccurrences = List.of();

    private ComboBox<ProductRanking> topProductsRanking;
    private DatePicker topProductsFromDatePicker;
    private DatePicker topProductsToDatePicker;
    private IntegerField topProductsLimitField;
    private Grid<TopProductDTO> topProductsGrid;
    private Span topProductsCountLabel;

    public ProductsView(FddbDataClient fddbDataClient, FddbExporterProperties properties) {
        this.fddbDataClient = fddbDataClient;
        this.fddbLinkPrefix = properties.getUi() != null && properties.getUi().getFddbLinkPrefix() != null
                ? properties.getUi().getFddbLinkPrefix()
                : "https://fddb.info";

        addClassName("products-view");
        getElement().getThemeList().add("padding-false");
        setSpacing(true);
        setPadding(false);
        setSizeFull();

        VerticalLayout headerLayout = new VerticalLayout();
        headerLayout.setPadding(true);
        headerLayout.setSpacing(true);
        applyResponsivePadding(headerLayout);
        headerLayout.add(new H2("Products"));
        headerLayout.add(new Paragraph("Explore a single product across your history, or rank the products you log most."));
        add(headerLayout);

        if (!isMongoDbEnabled(properties)) {
            VerticalLayout warningWrapper = new VerticalLayout();
            warningWrapper.setPadding(true);
            warningWrapper.setSpacing(true);
            applyResponsivePadding(warningWrapper);
            warningWrapper.add(createMongoDbDisabledWarning("Products"));
            add(warningWrapper);
            return;
        }

        tabSheet.setSizeFull();
        tabSheet.add("Explorer", createExplorerTab());
        tabSheet.add("Top Products", createTopProductsTab());

        add(tabSheet);
        setFlexGrow(1, tabSheet);
    }

    // ---------------------------------------------------------------------------------------------
    // Explorer tab
    // ---------------------------------------------------------------------------------------------

    private VerticalLayout createExplorerTab() {
        VerticalLayout layout = createTabLayout();
        // The summary block above the grid can be tall; let the whole tab scroll instead of letting
        // the fixed-height flex column squeeze the grid.
        layout.getStyle().set("overflow-y", "auto");

        productNameCombo = new ComboBox<>("Product Name");
        productNameCombo.setPlaceholder("Start typing, e.g. \"hafer\"...");
        productNameCombo.setWidthFull();
        productNameCombo.setAllowCustomValue(true);
        productNameCombo.setClearButtonVisible(true);
        // Lazy suggestions: resolve a fuzzy term to the exact stored names via /products/distinct.
        productNameCombo.setItems(query -> {
            String filter = query.getFilter().orElse("").trim();
            try {
                return fddbDataClient.getDistinctProductNames(filter.isEmpty() ? null : filter, DISTINCT_SUGGESTION_LIMIT)
                        .stream()
                        .skip(query.getOffset())
                        .limit(query.getLimit());
            } catch (ApiException e) {
                return Stream.empty();
            }
        });
        // Selecting a suggestion runs the explorer via the value-change listener below. Typing a
        // free-text term is reflected as the value (a programmatic setValue, so it does not re-fire
        // the client value-change) and explored directly, so a fuzzy term works without an exact match.
        productNameCombo.addCustomValueSetListener(e -> {
            productNameCombo.setValue(e.getDetail());
            explore(e.getDetail());
        });
        productNameCombo.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null && !e.getValue().isBlank()) {
                explore(e.getValue());
            }
        });

        explorerFromDatePicker = createOptionalDatePicker("From Date");
        explorerToDatePicker = createOptionalDatePicker("To Date");

        Button searchButton = new Button("Explore");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> explore(productNameCombo.getValue()));

        occurrencesCountLabel = createCountLabel();

        HorizontalLayout topRow = new HorizontalLayout(productNameCombo, explorerFromDatePicker,
                explorerToDatePicker, searchButton);
        topRow.addClassName("query-filter-row");
        topRow.setWidthFull();
        topRow.setAlignItems(Alignment.END);
        topRow.setFlexGrow(1, productNameCombo);
        topRow.getStyle().set("flex-wrap", "wrap");

        Paragraph hint = new Paragraph("Pick a suggested name for an exact match, or type any term to aggregate every "
                + "product whose name contains it. The date range narrows both the summary and the occurrences; click a "
                + "weekday bar to filter the occurrences to that day.");
        hint.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        summarySection = new Div();
        summarySection.setWidthFull();
        summarySection.setVisible(false);
        summarySection.getStyle().set("margin-top", "0.5rem").set("margin-bottom", "1rem");

        occurrencesGrid = createGrid(ProductWithDateDTO.class);
        // Give the grid a definite height with internal scrolling and stop it shrinking, so the tall
        // summary above it can never squeeze it to zero height (which would leave the virtual scroller
        // with an empty row pool even though the data is loaded).
        occurrencesGrid.setHeight("420px");
        occurrencesGrid.getStyle().set("flex-shrink", "0");
        occurrencesGrid.addColumn(dto -> dto.getDate() != null
                        ? dto.getDate().format(DATE_FORMAT) + " (" + shortDay(dto.getDate().getDayOfWeek()) + ")"
                        : "")
                .setHeader("Date").setSortable(true).setAutoWidth(true);
        occurrencesGrid.addColumn(dto -> dto.getProduct() != null ? dto.getProduct().getName() : "")
                .setHeader("Product Name").setSortable(true).setFlexGrow(3);
        occurrencesGrid.addColumn(dto -> dto.getProduct() != null ? dto.getProduct().getAmount() : "")
                .setHeader("Amount").setSortable(true).setAutoWidth(true);
        occurrencesGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getCalories()) : "")
                .setHeader("Calories").setSortable(true).setAutoWidth(true);
        occurrencesGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getFat()) : "")
                .setHeader("Fat (g)").setSortable(true).setAutoWidth(true);
        occurrencesGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getCarbs()) : "")
                .setHeader("Carbs (g)").setSortable(true).setAutoWidth(true);
        occurrencesGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getProtein()) : "")
                .setHeader("Protein (g)").setSortable(true).setAutoWidth(true);
        occurrencesGrid.addComponentColumn(dto -> dto.getProduct() != null ? createFddbLink(dto.getProduct()) : new Paragraph(""))
                .setHeader("Link").setAutoWidth(true);

        // Clicking an occurrence opens that day in the Entries view (which reads the ?date param).
        occurrencesGrid.addItemClickListener(event -> {
            ProductWithDateDTO selected = event.getItem();
            if (selected != null && selected.getDate() != null) {
                getUI().ifPresent(ui -> ui.navigate("entries?date=" + selected.getDate().format(DATE_FORMAT)));
            }
        });

        layout.add(topRow, hint, summarySection, occurrencesGrid);
        return layout;
    }

    private void explore(String rawTerm) {
        String term = rawTerm != null ? rawTerm.trim() : "";
        if (term.length() < MIN_SEARCH_LENGTH) {
            showError("Please enter at least " + MIN_SEARCH_LENGTH + " characters to explore");
            return;
        }

        LocalDate from = explorerFromDatePicker.getValue();
        LocalDate to = explorerToDatePicker.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            showError("From date must be before or equal to to date");
            return;
        }
        String fromStr = from != null ? from.format(DATE_FORMAT) : null;
        String toStr = to != null ? to.format(DATE_FORMAT) : null;

        try {
            ProductSummaryDTO summary = fddbDataClient.getProductSummary(term, fromStr, toStr);
            List<ProductWithDateDTO> occurrences =
                    fddbDataClient.searchProducts(term, null, fromStr, toStr, OCCURRENCE_LIMIT);

            allOccurrences = occurrences != null ? occurrences : List.of();
            selectedWeekdays.clear();

            renderSummary(summary);
            applyWeekdayFilter();

            int count = allOccurrences.size();
            if (count > 0) {
                showSuccess("Found " + count + " occurrences for \"" + term + "\"");
            } else {
                summarySection.setVisible(false);
                showError("No products found matching \"" + term + "\"");
            }
        } catch (ApiException e) {
            showError(e.getMessage());
            summarySection.setVisible(false);
            allOccurrences = List.of();
            selectedWeekdays.clear();
            clearGrid(occurrencesGrid, occurrencesCountLabel);
        }
    }

    /**
     * Applies the current weekday selection to the in-memory occurrence list. An empty selection
     * means "all weekdays". Filtering is done client-side so toggling a bar is instant.
     */
    private void applyWeekdayFilter() {
        List<ProductWithDateDTO> filtered = selectedWeekdays.isEmpty()
                ? allOccurrences
                : allOccurrences.stream()
                        .filter(o -> o.getDate() != null && selectedWeekdays.contains(o.getDate().getDayOfWeek()))
                        .toList();

        String suffix = selectedWeekdays.isEmpty() ? "" : " · " + selectedWeekdaysLabel();

        if (filtered.isEmpty() && !selectedWeekdays.isEmpty()) {
            // Filtered to weekday(s) with no loaded occurrences - keep an informative empty state
            // instead of hiding the count entirely.
            occurrencesGrid.setVisible(false);
            occurrencesGrid.setItems();
            occurrencesCountLabel.setText("0 occurrences" + suffix);
            occurrencesCountLabel.setVisible(true);
        } else {
            populateOccurrences(filtered, filtered.size() + " occurrences" + suffix);
        }
    }

    /**
     * Populates the occurrences grid without touching its height. Unlike {@link #populateGrid}, this
     * does not call {@code setAllRowsVisible}, so the grid keeps its fixed height and internal
     * scrolling (which reliably renders rows even beneath the tall summary block).
     */
    private void populateOccurrences(List<ProductWithDateDTO> items, String countText) {
        if (items != null && !items.isEmpty()) {
            occurrencesGrid.setVisible(true);
            occurrencesGrid.setItems(items);
            occurrencesCountLabel.setText(countText);
            occurrencesCountLabel.setVisible(true);
        } else {
            clearGrid(occurrencesGrid, occurrencesCountLabel);
        }
    }

    private String selectedWeekdaysLabel() {
        return Arrays.stream(DayOfWeek.values())
                .filter(selectedWeekdays::contains)
                .map(this::shortDay)
                .collect(Collectors.joining(", "));
    }

    private String shortDay(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    private Map<DayOfWeek, Long> weekdayCountsFromOccurrences() {
        Map<DayOfWeek, Long> counts = new EnumMap<>(DayOfWeek.class);
        for (ProductWithDateDTO occurrence : allOccurrences) {
            if (occurrence.getDate() != null) {
                counts.merge(occurrence.getDate().getDayOfWeek(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private void renderSummary(ProductSummaryDTO summary) {
        summarySection.removeAll();
        weekdayBars.clear();
        if (summary == null || summary.getTimesEaten() <= 0) {
            summarySection.setVisible(false);
            return;
        }

        Div statCards = createCardsGrid("150px");
        String range = summary.getFirstDate() != null && summary.getLastDate() != null
                ? summary.getFirstDate() + " ⮕ " + summary.getLastDate()
                : "—";
        statCards.add(
                createStatCard("Times eaten", String.valueOf(summary.getTimesEaten()), "logged occurrences"),
                createStatCard("Date range", range, "first ⮕ last logged"),
                createStatCard("Ø Calories", formatNumber(summary.getAverageCalories()) + " kcal", "per occurrence")
        );

        Div nutrientCards = createCardsGrid("120px");
        nutrientCards.add(
                createNutrientCard("Calories", formatNumber(summary.getTotalCalories()), "kcal", "🔥", null),
                createNutrientCard("Fat", formatNumber(summary.getTotalFat()), "g", "🧈", null),
                createNutrientCard("Carbs", formatNumber(summary.getTotalCarbs()), "g", "🍞", null),
                createNutrientCard("Protein", formatNumber(summary.getTotalProtein()), "g", "🥩", null)
        );

        VerticalLayout content = new VerticalLayout(statCards, nutrientCards);
        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();

        // Build the distribution from the loaded occurrences (the exact list the bars filter), so a
        // visible, clickable bar is always guaranteed to have matching rows.
        Component weekdayChart = createWeekdayDistribution(weekdayCountsFromOccurrences());
        if (weekdayChart != null) {
            content.add(weekdayChart);
        }

        List<String> matched = summary.getMatchedProductNames();
        if (matched != null && !matched.isEmpty()) {
            Span matchedLabel = new Span("Matched " + matched.size() + " product name(s): " + String.join(", ", matched));
            matchedLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
            content.add(matchedLabel);
        }

        summarySection.add(content);
        summarySection.setVisible(true);
    }

    /**
     * Renders the weekday distribution as horizontal CSS bars - the project ships no charting
     * library, so each bar is a plain div sized relative to the busiest weekday. Days with at least
     * one occurrence are clickable and toggle a weekday filter on the occurrences grid below.
     */
    private Component createWeekdayDistribution(Map<DayOfWeek, Long> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            return null;
        }
        long max = distribution.values().stream().mapToLong(Long::longValue).max().orElse(0);
        if (max <= 0) {
            return null;
        }

        VerticalLayout container = new VerticalLayout();
        container.addClassNames(LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);
        container.setWidthFull();
        container.setPadding(true);
        container.setSpacing(false);
        container.getStyle().set("box-sizing", "border-box");

        Span title = new Span("By day of the week");
        title.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD, LumoUtility.TextColor.SECONDARY);
        Span titleHint = new Span("  — click a day to filter the occurrences below");
        titleHint.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.TERTIARY);
        container.add(new Div(title, titleHint));

        for (DayOfWeek day : DayOfWeek.values()) {
            long count = distribution.getOrDefault(day, 0L);
            boolean clickable = count > 0;

            Span dayLabel = new Span(shortDay(day));
            dayLabel.getStyle().set("width", "3rem").set("flex", "0 0 3rem").set("font-size", "var(--lumo-font-size-s)");

            Div bar = new Div();
            double widthPct = max > 0 ? (count * 100.0 / max) : 0;
            bar.getStyle()
                    .set("height", "0.9rem")
                    .set("width", String.format(Locale.ENGLISH, "%.1f%%", widthPct))
                    .set("min-width", count > 0 ? "2px" : "0")
                    .set("background", BAR_COLOR)
                    .set("border-radius", "3px")
                    .set("transition", "opacity 0.15s ease");
            if (clickable) {
                weekdayBars.put(day, bar);
            }

            Div track = new Div(bar);
            track.getStyle().set("flex", "1 1 auto");

            Span countLabel = new Span(String.valueOf(count));
            countLabel.getStyle().set("width", "2.5rem").set("flex", "0 0 2.5rem").set("text-align", "right")
                    .set("font-size", "var(--lumo-font-size-s)");

            HorizontalLayout row = new HorizontalLayout(dayLabel, track, countLabel);
            row.setWidthFull();
            row.setSpacing(true);
            row.setPadding(false);
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.getStyle().set("margin-top", "0.25rem").set("border-radius", "4px");
            if (clickable) {
                row.getStyle().set("cursor", "pointer");
                row.getElement().setAttribute("title", "Filter occurrences to " + day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
                row.addClickListener(e -> toggleWeekday(day));
            } else {
                row.getStyle().set("opacity", "0.5");
            }
            container.add(row);
        }
        updateBarStyles();
        return container;
    }

    private void toggleWeekday(DayOfWeek day) {
        if (!selectedWeekdays.remove(day)) {
            selectedWeekdays.add(day);
        }
        updateBarStyles();
        applyWeekdayFilter();
    }

    /**
     * Highlights the selected weekday bars and dims the rest. With nothing selected every bar is
     * shown at full strength, signalling that no filter is active.
     */
    private void updateBarStyles() {
        boolean filterActive = !selectedWeekdays.isEmpty();
        weekdayBars.forEach((day, bar) -> {
            boolean active = !filterActive || selectedWeekdays.contains(day);
            bar.getStyle().set("opacity", active ? "1" : "0.3");
            bar.getStyle().set("box-shadow",
                    selectedWeekdays.contains(day) ? "0 0 0 2px var(--lumo-contrast-50pct)" : "none");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Top Products tab
    // ---------------------------------------------------------------------------------------------

    private VerticalLayout createTopProductsTab() {
        VerticalLayout layout = createTabLayout();

        topProductsRanking = new ComboBox<>("Rank By");
        topProductsRanking.setItems(ProductRanking.values());
        topProductsRanking.setValue(ProductRanking.FREQUENCY);
        topProductsRanking.setItemLabelGenerator(ranking -> switch (ranking) {
            case FREQUENCY -> "How often eaten";
            case CALORIES -> "Calories contributed";
            case FAT -> "Fat contributed";
            case CARBS -> "Carbs contributed";
            case PROTEIN -> "Protein contributed";
        });

        topProductsFromDatePicker = createOptionalDatePicker("From Date");
        topProductsToDatePicker = createOptionalDatePicker("To Date");

        topProductsLimitField = new IntegerField("Limit");
        topProductsLimitField.setValue(DEFAULT_TOP_PRODUCTS_LIMIT);
        topProductsLimitField.setMin(1);
        topProductsLimitField.setMax(MAX_TOP_PRODUCTS_LIMIT);
        topProductsLimitField.setStepButtonsVisible(true);

        Button loadButton = new Button("Load");
        loadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loadButton.addClickListener(e -> loadTopProducts());

        topProductsCountLabel = createCountLabel();

        HorizontalLayout topRow = new HorizontalLayout(topProductsRanking, topProductsFromDatePicker,
                topProductsToDatePicker, topProductsLimitField, loadButton, topProductsCountLabel);
        topRow.addClassName("query-filter-row");
        topRow.setWidthFull();
        topRow.setAlignItems(Alignment.END);
        topRow.getStyle().set("flex-wrap", "wrap");

        topProductsGrid = createGrid(TopProductDTO.class);
        topProductsGrid.addColumn(TopProductDTO::getName).setHeader("Product Name").setSortable(true).setFlexGrow(3);
        topProductsGrid.addColumn(TopProductDTO::getTimesEaten).setHeader("Times Eaten").setSortable(true).setAutoWidth(true);
        topProductsGrid.addColumn(dto -> formatNumber(dto.getTotalCalories())).setHeader("Total Calories").setSortable(true).setAutoWidth(true);
        topProductsGrid.addColumn(dto -> formatNumber(dto.getAverageCalories())).setHeader("Ø Calories").setSortable(true).setAutoWidth(true);
        topProductsGrid.addColumn(dto -> formatNumber(dto.getTotalFat())).setHeader("Total Fat (g)").setSortable(true).setAutoWidth(true);
        topProductsGrid.addColumn(dto -> formatNumber(dto.getTotalCarbs())).setHeader("Total Carbs (g)").setSortable(true).setAutoWidth(true);
        topProductsGrid.addColumn(dto -> formatNumber(dto.getTotalProtein())).setHeader("Total Protein (g)").setSortable(true).setAutoWidth(true);

        // Clicking a ranked product drills into the Explorer tab for its full profile.
        topProductsGrid.addItemClickListener(event -> {
            TopProductDTO selected = event.getItem();
            if (selected != null && selected.getName() != null) {
                tabSheet.setSelectedIndex(0);
                productNameCombo.setValue(selected.getName());
                explore(selected.getName());
            }
        });

        layout.add(topRow, topProductsGrid);
        return layout;
    }

    private void loadTopProducts() {
        LocalDate from = topProductsFromDatePicker.getValue();
        LocalDate to = topProductsToDatePicker.getValue();

        if (from != null && to != null && from.isAfter(to)) {
            showError("From date must be before or equal to to date");
            return;
        }

        Integer limit = topProductsLimitField.getValue();
        if (limit == null || limit < 1 || limit > MAX_TOP_PRODUCTS_LIMIT) {
            showError("Limit must be between 1 and " + MAX_TOP_PRODUCTS_LIMIT);
            return;
        }

        try {
            List<TopProductDTO> products = fddbDataClient.getTopProducts(
                    topProductsRanking.getValue(),
                    from != null ? from.format(DATE_FORMAT) : null,
                    to != null ? to.format(DATE_FORMAT) : null,
                    limit);
            int count = populateGrid(topProductsGrid, topProductsCountLabel, products, size(products) + " products");

            if (count > 0) {
                showSuccess("Loaded " + count + " products");
            } else {
                showError("No products found");
            }
        } catch (ApiException e) {
            showError(e.getMessage());
            clearGrid(topProductsGrid, topProductsCountLabel);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Shared helpers (mirrored from EntriesView)
    // ---------------------------------------------------------------------------------------------

    private VerticalLayout createTabLayout() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        layout.addClassName("data-query-tab-layout");
        return layout;
    }

    private DatePicker createOptionalDatePicker(String label) {
        DatePicker picker = new DatePicker(label);
        picker.setPlaceholder("optional");
        picker.setClearButtonVisible(true);
        picker.setI18n(createDatePickerI18n());
        return picker;
    }

    private Span createCountLabel() {
        Span label = new Span();
        label.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        label.setVisible(false);
        return label;
    }

    private <T> int populateGrid(Grid<T> grid, Span countLabel, List<T> items, String countText) {
        int count = items != null ? items.size() : 0;
        if (count > 0) {
            grid.setVisible(true);
            grid.setItems(items);
            expandGridSafely(grid, count);
            countLabel.setText(countText);
            countLabel.setVisible(true);
        } else {
            clearGrid(grid, countLabel);
        }
        return count;
    }

    private void clearGrid(Grid<?> grid, Span countLabel) {
        grid.setVisible(false);
        grid.setItems();
        countLabel.setVisible(false);
    }

    private int size(List<?> items) {
        return items != null ? items.size() : 0;
    }

    private <T> Grid<T> createGrid(Class<T> beanType) {
        Grid<T> grid = new Grid<>(beanType, false);
        grid.addClassName("data-query-grid");
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setVisible(false);
        return grid;
    }

    private void expandGridSafely(Grid<?> grid, int totalRows) {
        if (totalRows <= 0) {
            grid.getElement().getStyle().remove("height");
            grid.getElement().getStyle().remove("overflow");
            return;
        }

        final int maxPages = 10;
        int pageSize = Math.max(1, grid.getPageSize());
        int pages = (totalRows + pageSize - 1) / pageSize;

        if (pages <= maxPages) {
            grid.setPageSize(Math.max(pageSize, totalRows));
            grid.setAllRowsVisible(true);
            grid.getElement().getStyle().remove("height");
            grid.getElement().getStyle().remove("overflow");
        } else {
            int visibleRows = Math.min(totalRows, 20);
            int rowHeightPx = 40;
            grid.getElement().getStyle().set("height", (visibleRows * rowHeightPx) + "px");
            grid.getElement().getStyle().set("overflow", "visible");
        }
    }

    private Anchor createFddbLink(ProductDTO product) {
        if (product.getLink() != null && !product.getLink().isEmpty()) {
            String fullLink = product.getLink().startsWith("http") ? product.getLink() : fddbLinkPrefix + product.getLink();
            Anchor anchor = new Anchor(fullLink, "View");
            anchor.setTarget("_blank");
            return anchor;
        }
        return new Anchor();
    }
}
