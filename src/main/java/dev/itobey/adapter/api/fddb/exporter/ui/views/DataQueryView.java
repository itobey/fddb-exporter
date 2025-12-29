package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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

    // Date search tab
    private DatePicker searchDatePicker;
    private Grid<ProductDTO> dateProductsGrid;

    // Product search tab
    private TextField productSearchField;
    private Grid<ProductWithDateDTO> productSearchGrid;

    // TabSheet reference for navigation
    private final TabSheet tabSheet = new TabSheet();

    public DataQueryView(FddbDataClient fddbDataClient, FddbExporterProperties properties) {
        this.fddbDataClient = fddbDataClient;
        this.fddbLinkPrefix = properties.getUi() != null && properties.getUi().getFddbLinkPrefix() != null
                ? properties.getUi().getFddbLinkPrefix()
                : "https://fddb.info";

        addClassName("data-query-view");
        setSpacing(true);
        setPadding(true);
        setSizeFull();
        // Responsive padding - minimum on mobile for spacing from edges
        getStyle().set("padding", "clamp(0.5rem, 2vw, 1.5rem)");

        add(new H2("Data Query"));
        add(new Paragraph("Query and search your stored FDDB data."));

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
        // Minimum padding on mobile
        layout.getStyle().set("padding", "clamp(0.5rem, 2vw, 1rem)");

        Button loadButton = new Button("Load All Entries");
        loadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loadButton.addClickListener(e -> loadAllEntries());

        allEntriesGrid = new Grid<>(FddbDataDTO.class, false);
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
        // Set max height to prevent viewport overflow - accounts for navbar, header, tabs, form, and spacing
        allEntriesGrid.getStyle().set("max-height", "calc(100vh - 350px)");

        // Navigate to date search tab when a row is clicked
        allEntriesGrid.addItemClickListener(event -> {
            FddbDataDTO selectedData = event.getItem();
            if (selectedData != null && selectedData.getDate() != null) {
                // Switch to the "Search by Date" tab (index 1)
                tabSheet.setSelectedIndex(1);
                // Set the date and trigger search
                searchDatePicker.setValue(selectedData.getDate());
                searchByDate();
            }
        });

        layout.add(loadButton, allEntriesGrid);
        return layout;
    }

    private VerticalLayout createDateSearchTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        // Minimum padding on mobile
        layout.getStyle().set("padding", "clamp(0.5rem, 2vw, 1rem)");

        HorizontalLayout searchForm = new HorizontalLayout();
        searchForm.setAlignItems(Alignment.END);
        searchForm.setWidthFull();
        // Make form wrap on mobile
        searchForm.getStyle().set("flex-wrap", "wrap");
        searchForm.addClassNames(LumoUtility.Gap.SMALL);

        searchDatePicker = new DatePicker("Select Date");
        searchDatePicker.setValue(LocalDate.now().minusDays(1)); // Default to yesterday
        searchDatePicker.setWidthFull();

        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> searchByDate());
        // Make button responsive
        searchButton.getStyle().set("min-width", "100px");

        searchForm.add(searchDatePicker, searchButton);

        dateProductsGrid = new Grid<>(ProductDTO.class, false);
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
        // Set max height to prevent viewport overflow - accounts for navbar, header, tabs, form, and spacing
        dateProductsGrid.getStyle().set("max-height", "calc(100vh - 350px)");

        layout.add(searchForm, dateProductsGrid);
        // Don't set flex grow - let it size naturally
        return layout;
    }

    private VerticalLayout createProductSearchTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        // Minimum padding on mobile
        layout.getStyle().set("padding", "clamp(0.5rem, 2vw, 1rem)");

        HorizontalLayout searchForm = new HorizontalLayout();
        searchForm.setAlignItems(Alignment.END);
        searchForm.setWidthFull();
        // Make form wrap on mobile
        searchForm.getStyle().set("flex-wrap", "wrap");
        searchForm.addClassNames(LumoUtility.Gap.SMALL);

        productSearchField = new TextField("Product Name");
        productSearchField.setPlaceholder("Enter product name...");
        productSearchField.setWidthFull();
        // Make responsive
        productSearchField.getStyle().set("min-width", "200px").set("flex", "1 1 auto");

        // Trigger search on Enter key
        productSearchField.addKeyPressListener(event -> {
            if (event.getKey().getKeys().getFirst().equals("Enter")) {
                searchProducts();
            }
        });

        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> searchProducts());
        // Make button responsive
        searchButton.getStyle().set("min-width", "100px");

        searchForm.add(productSearchField, searchButton);

        productSearchGrid = new Grid<>(ProductWithDateDTO.class, false);
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
        // Set max height to prevent viewport overflow - accounts for navbar, header, tabs, form, and spacing
        productSearchGrid.getStyle().set("max-height", "calc(100vh - 350px)");

        layout.add(searchForm, productSearchGrid);
        return layout;
    }

    private void loadAllEntries() {
        try {
            List<FddbDataDTO> entries = fddbDataClient.getAllEntries();
            allEntriesGrid.setVisible(true); // Show grid when data is loaded
            allEntriesGrid.setItems(entries);
            // Always adjust height dynamically - shows all rows up to max viewport height
            allEntriesGrid.setAllRowsVisible(true);
            showSuccess("Loaded " + entries.size() + " entries");
        } catch (ApiException e) {
            showError(e.getMessage());
            allEntriesGrid.setVisible(true);
            allEntriesGrid.setItems();
            allEntriesGrid.setAllRowsVisible(true);
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
                // Always adjust height dynamically - shows all rows up to max viewport height
                dateProductsGrid.setAllRowsVisible(true);
                showSuccess("Found " + data.getProducts().size() + " products for " + date);
            } else {
                dateProductsGrid.setItems();
                dateProductsGrid.setAllRowsVisible(true);
                showError("No data found for " + date);
            }
        } catch (ApiException e) {
            showError(e.getMessage());
            dateProductsGrid.setVisible(true);
            dateProductsGrid.setItems();
            dateProductsGrid.setAllRowsVisible(true);
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
            // Always adjust height dynamically - shows all rows up to max viewport height
            productSearchGrid.setAllRowsVisible(true);
            showSuccess("Found " + products.size() + " matching products");
        } catch (ApiException e) {
            showError(e.getMessage());
            productSearchGrid.setVisible(true);
            productSearchGrid.setItems();
            productSearchGrid.setAllRowsVisible(true);
        }
    }


    private String formatNumber(double value) {
        return String.format("%.1f", value);
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

