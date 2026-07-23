package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductRanking;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.TopProductDTO;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.FddbDataClient;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

@Route(value = "query", layout = MainLayout.class)
@PageTitle("Data Query | FDDB Exporter")
public class DataQueryView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int DEFAULT_TOP_PRODUCTS_LIMIT = 20;
    private static final int MAX_TOP_PRODUCTS_LIMIT = 500;

    private final FddbDataClient fddbDataClient;
    private final String fddbLinkPrefix;

    private DatePicker entriesFromDatePicker;
    private DatePicker entriesToDatePicker;
    private Grid<FddbDataDTO> allEntriesGrid;
    private Span allEntriesCountLabel;

    private DatePicker searchDatePicker;
    private Div dateStatsCards;
    private Grid<ProductDTO> dateProductsGrid;
    private Span dateProductsCountLabel;

    private TextField productSearchField;
    private CheckboxGroup<DayOfWeek> daySelectionGroup;
    private Grid<ProductWithDateDTO> productSearchGrid;
    private Span productSearchCountLabel;

    private ComboBox<ProductRanking> topProductsRanking;
    private DatePicker topProductsFromDatePicker;
    private DatePicker topProductsToDatePicker;
    private IntegerField topProductsLimitField;
    private Grid<TopProductDTO> topProductsGrid;
    private Span topProductsCountLabel;

    private final TabSheet tabSheet = new TabSheet();

    public DataQueryView(FddbDataClient fddbDataClient, FddbExporterProperties properties) {
        this.fddbDataClient = fddbDataClient;
        this.fddbLinkPrefix = properties.getUi() != null && properties.getUi().getFddbLinkPrefix() != null
                ? properties.getUi().getFddbLinkPrefix()
                : "https://fddb.info";

        addClassName("data-query-view");
        getElement().getThemeList().add("padding-false");
        setSpacing(true);
        setPadding(false);
        setSizeFull();

        VerticalLayout headerLayout = new VerticalLayout();
        headerLayout.setPadding(true);
        headerLayout.setSpacing(true);
        applyResponsivePadding(headerLayout);

        headerLayout.add(new H2("Data Query"));
        headerLayout.add(new Paragraph("Query and search your stored FDDB data."));
        add(headerLayout);

        if (!isMongoDbEnabled(properties)) {
            VerticalLayout warningWrapper = new VerticalLayout();
            warningWrapper.setPadding(true);
            warningWrapper.setSpacing(true);
            applyResponsivePadding(warningWrapper);
            warningWrapper.add(createMongoDbDisabledWarning("Data Query"));
            add(warningWrapper);
            return;
        }

        tabSheet.setSizeFull();
        tabSheet.add("All Entries", createAllEntriesTab());
        tabSheet.add("Search by Date", createDateSearchTab());
        tabSheet.add("Search Products", createProductSearchTab());
        tabSheet.add("Top Products", createTopProductsTab());

        add(tabSheet);
        setFlexGrow(1, tabSheet);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> dateParams = event.getLocation().getQueryParameters().getParameters().get("date");
        if (dateParams != null) {
            dateParams.stream().findFirst().ifPresent(dateStr -> {
                try {
                    LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);
                    tabSheet.setSelectedIndex(1);
                    searchDatePicker.setValue(date);
                    searchByDate();
                } catch (Exception e) {
                    showError("Invalid date parameter: " + dateStr);
                }
            });
        }
    }

    private VerticalLayout createAllEntriesTab() {
        VerticalLayout layout = createTabLayout();

        entriesFromDatePicker = createOptionalDatePicker("From Date");
        entriesToDatePicker = createOptionalDatePicker("To Date");

        Button loadButton = new Button("Load Entries");
        loadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loadButton.addClickListener(e -> loadAllEntries());

        allEntriesCountLabel = createCountLabel();

        HorizontalLayout topRow = new HorizontalLayout(entriesFromDatePicker, entriesToDatePicker, loadButton, allEntriesCountLabel);
        topRow.setWidthFull();
        topRow.setAlignItems(Alignment.END);
        topRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Paragraph hint = new Paragraph("Set both dates to load a single range from the database instead of the whole history.");
        hint.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        allEntriesGrid = createGrid(FddbDataDTO.class);
        allEntriesGrid.addColumn(FddbDataDTO::getDate).setHeader("Date").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> dto.getProducts() != null ? dto.getProducts().size() : 0)
                .setHeader("Products").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalCalories())).setHeader("Calories").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalFat())).setHeader("Fat (g)").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalCarbs())).setHeader("Carbs (g)").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalProtein())).setHeader("Protein (g)").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalSugar())).setHeader("Sugar (g)").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalFibre())).setHeader("Fibre (g)").setSortable(true).setAutoWidth(true);

        allEntriesGrid.addItemClickListener(event -> {
            FddbDataDTO selectedData = event.getItem();
            if (selectedData != null && selectedData.getDate() != null) {
                tabSheet.setSelectedIndex(1);
                searchDatePicker.setValue(selectedData.getDate());
                searchByDate();
            }
        });

        layout.add(topRow, hint, allEntriesGrid);
        return layout;
    }

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

        topProductsGrid.addItemClickListener(event -> {
            TopProductDTO selected = event.getItem();
            if (selected != null && selected.getName() != null) {
                tabSheet.setSelectedIndex(2);
                productSearchField.setValue(selected.getName());
                searchProducts();
            }
        });

        layout.add(topRow, topProductsGrid);
        return layout;
    }

    private VerticalLayout createDateSearchTab() {
        VerticalLayout layout = createTabLayout();

        searchDatePicker = new DatePicker("Select Date");
        searchDatePicker.setPlaceholder("Select date...");
        searchDatePicker.setValue(LocalDate.now().minusDays(1));
        searchDatePicker.setWidthFull();
        searchDatePicker.setI18n(createDatePickerI18n());

        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> searchByDate());

        dateProductsCountLabel = createCountLabel();

        HorizontalLayout topRow = new HorizontalLayout(searchDatePicker, searchButton, dateProductsCountLabel);
        topRow.setWidthFull();
        topRow.setAlignItems(Alignment.END);
        topRow.setFlexGrow(1, searchDatePicker);

        dateStatsCards = createCardsGrid("120px");
        dateStatsCards.setVisible(false);
        dateStatsCards.getStyle()
                .set("width", "100%")
                .set("margin-top", "1rem")
                .set("margin-bottom", "1.5rem")
                .set("position", "relative")
                .set("z-index", "1");

        dateProductsGrid = createGrid(ProductDTO.class);
        dateProductsGrid.addColumn(ProductDTO::getName).setHeader("Product Name").setSortable(true).setAutoWidth(true);
        dateProductsGrid.addColumn(ProductDTO::getAmount).setHeader("Amount").setSortable(true).setAutoWidth(true);
        dateProductsGrid.addColumn(dto -> formatNumber(dto.getCalories())).setHeader("Calories").setSortable(true).setAutoWidth(true);
        dateProductsGrid.addColumn(dto -> formatNumber(dto.getFat())).setHeader("Fat (g)").setSortable(true).setAutoWidth(true);
        dateProductsGrid.addColumn(dto -> formatNumber(dto.getCarbs())).setHeader("Carbs (g)").setSortable(true).setAutoWidth(true);
        dateProductsGrid.addColumn(dto -> formatNumber(dto.getProtein())).setHeader("Protein (g)").setSortable(true).setAutoWidth(true);
        dateProductsGrid.addComponentColumn(this::createFddbLink).setHeader("Link").setAutoWidth(true);
        dateProductsGrid.addClassName("date-products-grid");
        dateProductsGrid.getStyle()
                .set("width", "100%")
                .set("position", "relative")
                .set("z-index", "0")
                .set("overflow", "visible");

        layout.add(topRow, dateStatsCards, dateProductsGrid);
        return layout;
    }

    private VerticalLayout createProductSearchTab() {
        VerticalLayout layout = createTabLayout();

        productSearchField = new TextField("Product Name");
        productSearchField.setPlaceholder("Enter product name...");
        productSearchField.setWidthFull();
        productSearchField.addKeyDownListener(Key.ENTER, e -> searchProducts());

        daySelectionGroup = new CheckboxGroup<>();
        daySelectionGroup.setLabel("Filter by Days (optional)");
        daySelectionGroup.setItems(DayOfWeek.values());
        daySelectionGroup.setItemLabelGenerator(day -> {
            String name = day.name();
            return name.charAt(0) + name.substring(1).toLowerCase();
        });
        daySelectionGroup.addClassName("day-selection-group");
        daySelectionGroup.getStyle()
                .set("display", "flex")
                .set("flex-direction", "row")
                .set("flex-wrap", "wrap");

        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> searchProducts());

        productSearchCountLabel = createCountLabel();

        HorizontalLayout topRow = new HorizontalLayout(productSearchField, searchButton, productSearchCountLabel);
        topRow.setWidthFull();
        topRow.setAlignItems(Alignment.END);
        topRow.setFlexGrow(1, productSearchField);

        HorizontalLayout daysRow = new HorizontalLayout(daySelectionGroup);
        daysRow.setWidthFull();
        daysRow.setAlignItems(Alignment.END);
        daysRow.setFlexGrow(1, daySelectionGroup);

        productSearchGrid = createGrid(ProductWithDateDTO.class);
        productSearchGrid.addColumn(ProductWithDateDTO::getDate).setHeader("Date").setSortable(true).setAutoWidth(true);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? dto.getProduct().getName() : "")
                .setHeader("Product Name").setSortable(true).setFlexGrow(3);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? dto.getProduct().getAmount() : "")
                .setHeader("Amount").setSortable(true).setAutoWidth(true);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getCalories()) : "")
                .setHeader("Calories").setSortable(true).setAutoWidth(true);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getFat()) : "")
                .setHeader("Fat (g)").setSortable(true).setAutoWidth(true);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getCarbs()) : "")
                .setHeader("Carbs (g)").setSortable(true).setAutoWidth(true);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getProtein()) : "")
                .setHeader("Protein (g)").setSortable(true).setAutoWidth(true);
        productSearchGrid.addComponentColumn(dto -> dto.getProduct() != null ? createFddbLink(dto.getProduct()) : new Paragraph(""))
                .setHeader("Link").setAutoWidth(true);

        productSearchGrid.addItemClickListener(event -> {
            ProductWithDateDTO selectedData = event.getItem();
            if (selectedData != null && selectedData.getDate() != null) {
                tabSheet.setSelectedIndex(1);
                searchDatePicker.setValue(selectedData.getDate());
                searchByDate();
            }
        });

        layout.add(topRow, daysRow, productSearchGrid);
        return layout;
    }

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

    /**
     * Populate a grid and its count label with the given items, or hide/clear both when the result is
     * empty. Notifications are left to the caller since their wording differs per tab.
     *
     * @return the number of items shown
     */
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
        return configureGrid(new Grid<>(beanType, false));
    }

    private <T> Grid<T> createGrid() {
        return configureGrid(new Grid<>());
    }

    private <T> Grid<T> configureGrid(Grid<T> grid) {
        grid.addClassName("data-query-grid");
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setVisible(false);
        // Removed unconditional setAllRowsVisible(true) to avoid Vaadin fetching too many pages.
        return grid;
    }

    // Helper: expand grid safely. Calls setAllRowsVisible(true) only when the number of pages
    // that would be fetched is <= MAX_PAGES. Otherwise falls back to a bounded height (heightByRows)
    // to avoid triggering Vaadin's max page-count exception and performance issues.
    private void expandGridSafely(Grid<?> grid, int totalRows) {
        if (totalRows <= 0) {
            // Nothing to show — reset height and return
            grid.getElement().getStyle().remove("height");
            grid.getElement().getStyle().remove("overflow");
            return;
        }

        final int MAX_PAGES = 10; // Vaadin's internal limit; be conservative
        int pageSize = Math.max(1, grid.getPageSize());
        int pages = (totalRows + pageSize - 1) / pageSize;

        if (pages <= MAX_PAGES) {
            // Safe to request all rows — set page size large enough so that fetching all rows
            // doesn't require more than MAX_PAGES requests.
            grid.setPageSize(Math.max(pageSize, totalRows));
            grid.setAllRowsVisible(true);
            // Remove any explicit height we may have set previously
            grid.getElement().getStyle().remove("height");
            grid.getElement().getStyle().remove("overflow");
        } else {
            // Too many pages: avoid fetching everything. Show a reasonable number of rows
            // so the grid is expanded visually but doesn't try to download the whole dataset.
            int visibleRows = Math.min(totalRows, 20); // choose a safe default (tweakable)
            // Estimate row height (including padding). Adjust if needed for your theme.
            int rowHeightPx = 40;
            int heightPx = visibleRows * rowHeightPx;
            grid.getElement().getStyle().set("height", heightPx + "px");
            // ensure overflow is visible so items can overflow visually if needed
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

    private void loadAllEntries() {
        LocalDate from = entriesFromDatePicker.getValue();
        LocalDate to = entriesToDatePicker.getValue();

        if (from != null && to != null && from.isAfter(to)) {
            showError("From date must be before or equal to to date");
            return;
        }
        if ((from == null) != (to == null)) {
            showError("Please set both dates to load a range, or clear both to load everything");
            return;
        }

        try {
            List<FddbDataDTO> entries = from != null
                    ? fddbDataClient.getByDateRange(from.format(DATE_FORMAT), to.format(DATE_FORMAT), true)
                    : fddbDataClient.getAllEntries();
            int count = populateGrid(allEntriesGrid, allEntriesCountLabel, entries, size(entries) + " entries");

            if (count > 0) {
                showSuccess("Loaded " + count + " entries");
            } else {
                showError("No entries found");
            }
        } catch (ApiException e) {
            showError(e.getMessage());
            clearGrid(allEntriesGrid, allEntriesCountLabel);
        }
    }

    private void searchByDate() {
        if (searchDatePicker.getValue() == null) {
            showError("Please select a date");
            return;
        }

        try {
            String date = searchDatePicker.getValue().format(DATE_FORMAT);
            FddbDataDTO data = fddbDataClient.getByDate(date);
            List<ProductDTO> products = data != null ? data.getProducts() : null;
            int count = populateGrid(dateProductsGrid, dateProductsCountLabel, products, size(products) + " products");

            if (count > 0) {
                dateStatsCards.removeAll();
                dateStatsCards.add(
                        createNutrientCard("Calories", formatNumber(data.getTotalCalories()), "kcal", "🔥", null),
                        createNutrientCard("Fat", formatNumber(data.getTotalFat()), "g", "🧈", null),
                        createNutrientCard("Carbs", formatNumber(data.getTotalCarbs()), "g", "🍞", null),
                        createNutrientCard("Sugar", formatNumber(data.getTotalSugar()), "g", "🍬", null),
                        createNutrientCard("Protein", formatNumber(data.getTotalProtein()), "g", "🥩", null),
                        createNutrientCard("Fibre", formatNumber(data.getTotalFibre()), "g", "🥦", null)
                );
                dateStatsCards.setVisible(true);
                showSuccess("Found " + count + " products for " + date);
            } else {
                dateStatsCards.setVisible(false);
                showError("No data found for " + date);
            }
        } catch (ApiException e) {
            showError(e.getMessage());
            dateStatsCards.setVisible(false);
            clearGrid(dateProductsGrid, dateProductsCountLabel);
        }
    }

    private void searchProducts() {
        String searchTerm = productSearchField.getValue();
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            showError("Please enter a product name to search");
            return;
        }

        String trimmed = searchTerm.trim();
        if (trimmed.length() == 1) {
            // Explicitly require at least 2 characters to avoid overly broad or expensive searches
            showError("Please enter at least 2 characters to search");
            return;
        }

        try {
            List<DayOfWeek> selectedDays = daySelectionGroup.getValue() != null && !daySelectionGroup.getValue().isEmpty()
                    ? daySelectionGroup.getValue().stream().toList()
                    : null;

            List<ProductWithDateDTO> products = fddbDataClient.searchProducts(trimmed, selectedDays);
            boolean daysFiltered = selectedDays != null && !selectedDays.isEmpty();

            String daysInfo = daysFiltered ? " (filtered by " + selectedDays.size() + " day(s))" : "";
            int productSearchCount = populateGrid(productSearchGrid, productSearchCountLabel, products,
                    size(products) + " results" + daysInfo);

            if (productSearchCount > 0) {
                showSuccess("Found " + productSearchCount + " matching products" + daysInfo);
            } else {
                showError("No products found matching \"" + trimmed + "\""
                        + (daysFiltered ? " for the selected day(s)" : ""));
            }
        } catch (ApiException e) {
            showError(e.getMessage());
            clearGrid(productSearchGrid, productSearchCountLabel);
        }
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
