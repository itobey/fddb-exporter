package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
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
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.FddbDataClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * View for querying FDDB data.
 */
@Route(value = "query", layout = MainLayout.class)
@PageTitle("Data Query | FDDB Exporter")
public class DataQueryView extends VerticalLayout implements BeforeEnterObserver {

    private final FddbDataClient fddbDataClient;
    private final String fddbLinkPrefix;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // All entries tab
    private Grid<FddbDataDTO> allEntriesGrid;
    private Span allEntriesCountLabel;

    // Date search tab
    private DatePicker searchDatePicker;
    private Grid<ProductDTO> dateProductsGrid;
    private Span dateProductsCountLabel;

    // Product search tab
    private TextField productSearchField;
    private Grid<ProductWithDateDTO> productSearchGrid;
    private Span productSearchCountLabel;

    // TabSheet reference for navigation
    private final TabSheet tabSheet = new TabSheet();

    public DataQueryView(FddbDataClient fddbDataClient, FddbExporterProperties properties) {
        this.fddbDataClient = fddbDataClient;
        this.fddbLinkPrefix = properties.getUi() != null && properties.getUi().getFddbLinkPrefix() != null
                ? properties.getUi().getFddbLinkPrefix()
                : "https://fddb.info";

        addClassName("data-query-view");
        // Prevent global mobile padding rules from forcing extra padding; set theme attribute used by CSS selectors
        getElement().getThemeList().add("padding-false");

        // Remove padding from the main view to allow full-width tables
        setSpacing(true);
        setPadding(false);
        setSizeFull();

        VerticalLayout headerLayout = new VerticalLayout();
        headerLayout.setPadding(true);
        headerLayout.setSpacing(true);
        headerLayout.getStyle().set("padding", "clamp(0.5rem, 2vw, 1.5rem)");

        headerLayout.add(new H2("Data Query"));
        headerLayout.add(new Paragraph("Query and search your stored FDDB data."));
        add(headerLayout);

        tabSheet.setSizeFull();

        tabSheet.add("All Entries", createAllEntriesTab());
        tabSheet.add("Search by Date", createDateSearchTab());
        tabSheet.add("Search Products", createProductSearchTab());

        add(tabSheet);
        setFlexGrow(1, tabSheet);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Check if there's a date parameter in the URL
        List<String> dateParams = event.getLocation().getQueryParameters().getParameters().get("date");
        if (dateParams != null) {
            dateParams.stream()
                    .findFirst()
                    .ifPresent(dateStr -> {
                        try {
                            LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);
                            // Switch to the "Search by Date" tab (index 1)
                            tabSheet.setSelectedIndex(1);
                            // Set the date and trigger search
                            searchDatePicker.setValue(date);
                            searchByDate();
                        } catch (Exception e) {
                            showError("Invalid date parameter: " + dateStr);
                        }
                    });
        }
    }

    private VerticalLayout createAllEntriesTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        layout.addClassName("data-query-tab-layout");
        layout.getStyle()
                .set("padding-left", "clamp(0rem, 2vw, 1rem)")
                .set("padding-right", "clamp(0rem, 2vw, 1rem)")
                .set("padding-top", "0.5rem")
                .set("padding-bottom", "0.5rem");

        Button loadButton = new Button("Load All Entries");
        loadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loadButton.addClickListener(e -> loadAllEntries());

        allEntriesCountLabel = new Span();
        allEntriesCountLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        allEntriesCountLabel.setVisible(false);
        allEntriesCountLabel.getStyle()
                .set("margin-bottom", "0.5rem")
                .set("white-space", "nowrap")
                .set("flex", "0 1 auto")
                .set("max-width", "clamp(8rem, 40%, 14rem)")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis");
        allEntriesCountLabel.getStyle().set("display", "inline-block").set("min-width", "0");

        allEntriesCountLabel.getStyle().set("flex", "0 1 auto");
        HorizontalLayout topRow = new HorizontalLayout(loadButton, allEntriesCountLabel);
        topRow.setWidthFull();
        topRow.setAlignItems(Alignment.CENTER);
        topRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topRow.setSpacing(false);

        allEntriesGrid = new Grid<>(FddbDataDTO.class, false);
        allEntriesGrid.addClassName("data-query-grid");
        allEntriesGrid.addClassName("all-entries-grid");
        allEntriesGrid.addColumn(FddbDataDTO::getDate).setHeader("Date").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> dto.getProducts() != null ? dto.getProducts().size() : 0)
                .setHeader("Products").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalCalories())).setHeader("Calories").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalFat())).setHeader("Fat (g)").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalCarbs())).setHeader("Carbs (g)").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalProtein())).setHeader("Protein (g)").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalSugar())).setHeader("Sugar (g)").setSortable(true).setAutoWidth(true);
        allEntriesGrid.addColumn(dto -> formatNumber(dto.getTotalFibre())).setHeader("Fibre (g)").setSortable(true).setAutoWidth(true);

        allEntriesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        allEntriesGrid.setVisible(false); // Hide initially
        allEntriesGrid.setAllRowsVisible(true); // Adjust height based on content

        allEntriesGrid.addItemClickListener(event -> {
            FddbDataDTO selectedData = event.getItem();
            if (selectedData != null && selectedData.getDate() != null) {
                tabSheet.setSelectedIndex(1);
                searchDatePicker.setValue(selectedData.getDate());
                searchByDate();
            }
        });

        layout.add(topRow, allEntriesGrid);
        return layout;
    }

    private VerticalLayout createDateSearchTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        layout.addClassName("data-query-tab-layout");
        layout.getStyle()
                .set("padding-left", "clamp(0rem, 2vw, 1rem)")
                .set("padding-right", "clamp(0rem, 2vw, 1rem)")
                .set("padding-top", "0.25rem")
                .set("padding-bottom", "0.5rem");

        VerticalLayout searchForm = new VerticalLayout();
        searchForm.setWidthFull();
        searchForm.setSpacing(false);
        searchForm.setPadding(false);
        searchForm.getStyle().set("margin", "0");
        searchForm.setSpacing(false);
        searchForm.addClassName("search-form");
        searchForm.getStyle().set("padding-left", "clamp(0.5rem, 0vw, 0rem)");
        searchForm.getStyle().set("padding-right", "clamp(0.5rem, 0vw, 0rem)");
        searchForm.getStyle().set("margin-top", "0").set("margin-bottom", "0").set("padding-top", "0").set("padding-bottom", "0");

        Span searchDateLabel = new Span("Select Date");
        searchDateLabel.getStyle().set("font-size", "0.9rem").set("margin", "0").set("padding", "0").set("color", "var(--lumo-secondary-text-color)");

        searchDatePicker = new DatePicker();
        searchDatePicker.setPlaceholder("Select date...");
        searchDatePicker.setValue(LocalDate.now().minusDays(1)); // Default to yesterday
        searchDatePicker.setWidthFull();
        searchDatePicker.setI18n(createDatePickerI18n());

        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> searchByDate());
        searchButton.getStyle().set("min-width", "100px");
        searchDatePicker.getStyle().set("margin-top", "0").set("margin-bottom", "0");

        HorizontalLayout buttonRow = new HorizontalLayout(searchButton);
        buttonRow.setPadding(false);
        buttonRow.setSpacing(false);
        buttonRow.setAlignItems(Alignment.CENTER);
        buttonRow.setWidthFull();
        buttonRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        buttonRow.getStyle().set("flex-wrap", "nowrap");
        buttonRow.getStyle().set("margin-bottom", "0");
        searchForm.add(searchDateLabel, searchDatePicker, buttonRow);

        dateProductsCountLabel = new Span();
        dateProductsCountLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        dateProductsCountLabel.setVisible(false);
        dateProductsCountLabel.getStyle()
                .set("margin-bottom", "0.1rem")
                .set("white-space", "nowrap")
                .set("flex", "0 0 auto")
                .set("max-width", "clamp(4rem, 28%, 9rem)")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis");
        dateProductsCountLabel.getStyle().set("display", "inline-block").set("min-width", "0");
        dateProductsCountLabel.getStyle().set("flex", "0 1 auto");
        buttonRow.add(dateProductsCountLabel);

        dateProductsGrid = new Grid<>(ProductDTO.class, false);
        dateProductsGrid.addClassName("data-query-grid");
        // Keep the grid visible on all screen sizes; remove mobile-only card duplication
        dateProductsGrid.addColumn(ProductDTO::getName).setHeader("Product Name").setSortable(true).setFlexGrow(3).setAutoWidth(false);
        dateProductsGrid.addColumn(ProductDTO::getAmount).setHeader("Amount").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        dateProductsGrid.addColumn(dto -> formatNumber(dto.getCalories())).setHeader("Calories").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        dateProductsGrid.addColumn(dto -> formatNumber(dto.getFat())).setHeader("Fat (g)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        dateProductsGrid.addColumn(dto -> formatNumber(dto.getCarbs())).setHeader("Carbs (g)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        dateProductsGrid.addColumn(dto -> formatNumber(dto.getProtein())).setHeader("Protein (g)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        dateProductsGrid.addComponentColumn(dto -> {
            if (dto.getLink() != null && !dto.getLink().isEmpty()) {
                String fullLink = dto.getLink().startsWith("http") ? dto.getLink() : fddbLinkPrefix + dto.getLink();
                Anchor anchor = new Anchor(fullLink, "View");
                anchor.setTarget("_blank");
                return anchor;
            }
            return new Paragraph("");
        }).setHeader("Link").setAutoWidth(true);

        dateProductsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        dateProductsGrid.setVisible(false); // Hide initially
        dateProductsGrid.setAllRowsVisible(true); // Adjust height based on content

        HorizontalLayout dateTopRow = new HorizontalLayout(searchForm);
        dateTopRow.setWidthFull();
        dateTopRow.setAlignItems(Alignment.CENTER);
        dateTopRow.setSpacing(false);
        dateTopRow.setPadding(false);
        dateTopRow.getStyle().set("margin", "0");
        dateTopRow.getStyle().set("padding-bottom", "0");
        dateTopRow.getStyle().set("margin-top", "-0.5rem");
        dateTopRow.getStyle().set("margin-bottom", "0rem");
        searchForm.getStyle().set("margin-bottom", "-0.2rem");

        layout.add(dateTopRow, dateProductsGrid);
        return layout;
    }

    private VerticalLayout createProductSearchTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        layout.addClassName("data-query-tab-layout");
        layout.getStyle()
                .set("padding-left", "clamp(0rem, 2vw, 1rem)")
                .set("padding-right", "clamp(0rem, 2vw, 1rem)")
                .set("padding-top", "0.25rem")
                .set("padding-bottom", "0.5rem");

        VerticalLayout searchForm = new VerticalLayout();
        searchForm.setWidthFull();
        searchForm.setSpacing(false);
        searchForm.setPadding(false);
        searchForm.getStyle().set("margin", "0");
        searchForm.setSpacing(false);
        searchForm.addClassName("search-form");
        searchForm.getStyle().set("padding-left", "clamp(0.5rem, 0vw, 0rem)");
        searchForm.getStyle().set("padding-right", "clamp(0.5rem, 0vw, 0rem)");
        searchForm.getStyle().set("margin-top", "0").set("margin-bottom", "0").set("padding-top", "0").set("padding-bottom", "0");

        Span productSearchLabel = new Span("Product Name");
        productSearchLabel.getStyle().set("font-size", "0.9rem").set("margin", "0").set("padding", "0").set("color", "var(--lumo-secondary-text-color)");

        productSearchField = new TextField();
        productSearchField.setPlaceholder("Enter product name...");
        productSearchField.setWidthFull();
        productSearchField.getStyle().set("margin-top", "0").set("margin-bottom", "0");
        productSearchField.getStyle().set("min-width", "200px").set("flex", "1 1 auto");

        productSearchField.addKeyDownListener(Key.ENTER, e -> searchProducts());

        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> searchProducts());
        searchButton.getStyle().set("min-width", "100px");

        HorizontalLayout productButtonRow = new HorizontalLayout(searchButton);
        productButtonRow.setPadding(false);
        productButtonRow.setSpacing(false);
        productButtonRow.setAlignItems(Alignment.CENTER);
        productButtonRow.setWidthFull();
        productButtonRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        productButtonRow.getStyle().set("flex-wrap", "nowrap");
        productButtonRow.getStyle().set("margin-bottom", "0");
        searchForm.add(productSearchLabel, productSearchField, productButtonRow);

        productSearchCountLabel = new Span();
        productSearchCountLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        productSearchCountLabel.setVisible(false);
        productSearchCountLabel.getStyle()
                .set("margin-bottom", "0.1rem")
                .set("white-space", "nowrap")
                .set("flex", "0 0 auto")
                .set("max-width", "clamp(4rem, 28%, 9rem)")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis");
        productSearchCountLabel.getStyle().set("display", "inline-block").set("min-width", "0");
        productSearchCountLabel.getStyle().set("flex", "0 1 auto");
        productButtonRow.add(productSearchCountLabel);

        productSearchGrid = new Grid<>(ProductWithDateDTO.class, false);
        productSearchGrid.addClassName("data-query-grid");
        // Keep the grid visible on all screen sizes; remove mobile-only card duplication
        productSearchGrid.addColumn(ProductWithDateDTO::getDate).setHeader("Date").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? dto.getProduct().getName() : "").setHeader("Product Name").setSortable(true).setFlexGrow(3).setAutoWidth(false);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? dto.getProduct().getAmount() : "").setHeader("Amount").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getCalories()) : "").setHeader("Calories").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getFat()) : "").setHeader("Fat (g)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getCarbs()) : "").setHeader("Carbs (g)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        productSearchGrid.addColumn(dto -> dto.getProduct() != null ? formatNumber(dto.getProduct().getProtein()) : "").setHeader("Protein (g)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        productSearchGrid.addComponentColumn(dto -> {
            if (dto.getProduct() != null && dto.getProduct().getLink() != null && !dto.getProduct().getLink().isEmpty()) {
                String fullLink = dto.getProduct().getLink().startsWith("http") ? dto.getProduct().getLink() : fddbLinkPrefix + dto.getProduct().getLink();
                Anchor anchor = new Anchor(fullLink, "View");
                anchor.setTarget("_blank");
                return anchor;
            }
            return new Paragraph("");
        }).setHeader("Link").setAutoWidth(true);

        productSearchGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        productSearchGrid.setVisible(false); // Hide initially
        productSearchGrid.setAllRowsVisible(true); // Adjust height based on content

        HorizontalLayout productTopRow = new HorizontalLayout(searchForm);
        productTopRow.setWidthFull();
        productTopRow.setAlignItems(Alignment.CENTER);
        productTopRow.setSpacing(false);
        productTopRow.setPadding(false);
        productTopRow.getStyle().set("margin", "0");
        productTopRow.getStyle().set("padding-bottom", "0");
        productTopRow.getStyle().set("margin-top", "-0.5rem");
        productTopRow.getStyle().set("margin-bottom", "0rem");
        searchForm.getStyle().set("margin-bottom", "-0.2rem");

        layout.add(productTopRow, productSearchGrid);
        return layout;
    }

    private void loadAllEntries() {
        try {
            List<FddbDataDTO> entries = fddbDataClient.getAllEntries();
            allEntriesGrid.setVisible(true); // Show grid when data is loaded
            allEntriesGrid.setItems(entries);
            allEntriesGrid.setAllRowsVisible(true);

            allEntriesCountLabel.setText(entries.size() + "\u00A0entries");
            allEntriesCountLabel.setVisible(true);

            showSuccess("Loaded " + entries.size() + " entries");
        } catch (ApiException e) {
            showError(e.getMessage());
            allEntriesGrid.setVisible(true);
            allEntriesGrid.setItems();
            allEntriesGrid.setAllRowsVisible(true);
            allEntriesCountLabel.setVisible(false);
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
            dateProductsGrid.setVisible(true); // Show grid when search is performed
            if (data != null && data.getProducts() != null) {
                dateProductsGrid.setItems(data.getProducts());
                dateProductsGrid.setAllRowsVisible(true);

                dateProductsCountLabel.setText(data.getProducts().size() + "\u00A0products");
                dateProductsCountLabel.setVisible(true);

                showSuccess("Found " + data.getProducts().size() + " products for " + date);
            } else {
                dateProductsGrid.setItems();
                dateProductsGrid.setAllRowsVisible(true);
                dateProductsCountLabel.setVisible(false);
                showError("No data found for " + date);
            }
        } catch (ApiException e) {
            showError(e.getMessage());
            dateProductsGrid.setVisible(true);
            dateProductsGrid.setItems();
            dateProductsGrid.setAllRowsVisible(true);
            dateProductsCountLabel.setVisible(false);
        }
    }

    private void searchProducts() {
        String searchTerm = productSearchField.getValue();
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            showError("Please enter a product name to search");
            return;
        }

        try {
            List<ProductWithDateDTO> products = fddbDataClient.searchProducts(searchTerm.trim());
            productSearchGrid.setVisible(true); // Show grid when search is performed
            productSearchGrid.setItems(products);
            productSearchGrid.setAllRowsVisible(true);

            productSearchCountLabel.setText(products.size() + "\u00A0results");
            productSearchCountLabel.setVisible(true);

            showSuccess("Found " + products.size() + " matching products");
        } catch (ApiException e) {
            showError(e.getMessage());
            productSearchGrid.setVisible(true);
            productSearchGrid.setItems();
            productSearchGrid.setAllRowsVisible(true);
            productSearchCountLabel.setVisible(false);
        }
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

