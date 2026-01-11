package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
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
        // Remove the view-level padding to allow edge-to-edge content
        // getStyle().set("padding", "clamp(0.5rem, 2vw, 1.5rem)");

        // Allow header to have padding
        VerticalLayout headerLayout = new VerticalLayout();
        headerLayout.setPadding(true);
        headerLayout.setSpacing(true);
        // Responsive padding - match previous view padding
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

    // Card containers for mobile view (only for detail views with smaller datasets)
    private VerticalLayout dateProductsCardsContainer;
    private VerticalLayout productSearchCardsContainer;

    private VerticalLayout createAllEntriesTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        layout.addClassName("data-query-tab-layout");
        // Responsive padding: minimal top/bottom, zero horizontal on mobile for full-width tables
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
        // Keep the count on a single line and prevent it from shrinking/wrapping
        allEntriesCountLabel.getStyle()
                .set("margin-bottom", "0.5rem")
                .set("white-space", "nowrap")
                .set("flex", "0 0 auto");
        // Also ensure it is rendered as inline-block so nowrap is respected across layouts
        allEntriesCountLabel.getStyle().set("display", "inline-block").set("min-width", "0");

        // Put the load button and count label into a single row so the count (amount) is aligned to the right above the table
        HorizontalLayout topRow = new HorizontalLayout(loadButton, allEntriesCountLabel);
        topRow.setWidthFull();
        topRow.setAlignItems(Alignment.CENTER);
        topRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topRow.setSpacing(false);

        allEntriesGrid = new Grid<>(FddbDataDTO.class, false);
        allEntriesGrid.addClassName("data-query-grid");
        // Note: No desktop-only class here - Grid works well for large datasets on both desktop and mobile
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
        // Max height is handled by CSS responsively (desktop only)

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

        layout.add(topRow, allEntriesGrid);
        return layout;
    }

    private VerticalLayout createDateSearchTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        layout.addClassName("data-query-tab-layout");
        // Responsive padding: minimal top/bottom, zero horizontal on mobile for full-width tables
        layout.getStyle()
                .set("padding-left", "clamp(0rem, 2vw, 1rem)")
                .set("padding-right", "clamp(0rem, 2vw, 1rem)")
                .set("padding-top", "0.5rem")
                .set("padding-bottom", "0.5rem");

        VerticalLayout searchForm = new VerticalLayout();
        searchForm.setWidthFull();
        searchForm.setSpacing(false);
        searchForm.addClassNames(LumoUtility.Gap.SMALL);
        // Add a class so CSS can target padding/margins consistently
        searchForm.addClassName("search-form");

        // Add padding to the form container on mobile so it doesn't touch edges
        searchForm.getStyle().set("padding-left", "clamp(0.5rem, 0vw, 0rem)");
        searchForm.getStyle().set("padding-right", "clamp(0.5rem, 0vw, 0rem)");

        searchDatePicker = new DatePicker("Select Date");
        searchDatePicker.setValue(LocalDate.now().minusDays(1)); // Default to yesterday
        searchDatePicker.setWidthFull();
        searchDatePicker.setI18n(createDatePickerI18n());

        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> searchByDate());
        // Make button responsive
        searchButton.getStyle().set("min-width", "100px");

        HorizontalLayout buttonRow = new HorizontalLayout(searchButton);
        buttonRow.setPadding(false);
        buttonRow.setSpacing(false);
        buttonRow.setAlignItems(Alignment.CENTER);
        searchForm.add(searchDatePicker, buttonRow);

        dateProductsCountLabel = new Span();
        dateProductsCountLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        dateProductsCountLabel.setVisible(false);
        // Prevent wrapping so counts like "19 results" stay on a single line
        dateProductsCountLabel.getStyle()
                .set("margin-bottom", "0.5rem")
                .set("white-space", "nowrap")
                .set("flex", "0 0 auto");
        // Ensure inline-block so nowrap and flex interaction behave consistently
        dateProductsCountLabel.getStyle().set("display", "inline-block").set("min-width", "0");

        // Put the search form and the count label into one row so the count appears on the right above the grid
        // Align items to the end so the count vertically lines up with the button row rather than the taller input
        HorizontalLayout dateTopRow = new HorizontalLayout(searchForm, dateProductsCountLabel);
        dateTopRow.setWidthFull();
        dateTopRow.setAlignItems(Alignment.END);
        dateTopRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        dateTopRow.setSpacing(false);
        // Ensure the searchForm expands to take remaining width so the count label stays at the far right
        dateTopRow.expand(searchForm);

        dateProductsGrid = new Grid<>(ProductDTO.class, false);
        dateProductsGrid.addClassName("data-query-grid");
        dateProductsGrid.addClassName("desktop-only");
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
        // Max height is handled by CSS responsively (desktop only)

        // Create cards container for mobile
        dateProductsCardsContainer = new VerticalLayout();
        dateProductsCardsContainer.addClassName("mobile-only");
        dateProductsCardsContainer.addClassName("cards-container");
        dateProductsCardsContainer.setSpacing(true);
        dateProductsCardsContainer.setPadding(false);
        dateProductsCardsContainer.setVisible(false);
        // Add padding so cards don't touch edges (consistent with form)
        dateProductsCardsContainer.getStyle()
                .set("padding-left", "0.5rem")
                .set("padding-right", "0.5rem");

        layout.add(dateTopRow, dateProductsGrid, dateProductsCardsContainer);
        // Don't set flex grow - let it size naturally
        return layout;
    }

    private VerticalLayout createProductSearchTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        layout.addClassName("data-query-tab-layout");
        // Responsive padding: minimal top/bottom, zero horizontal on mobile for full-width tables
        layout.getStyle()
                .set("padding-left", "clamp(0rem, 2vw, 1rem)")
                .set("padding-right", "clamp(0rem, 2vw, 1rem)")
                .set("padding-top", "0.5rem")
                .set("padding-bottom", "0.5rem");

        VerticalLayout searchForm = new VerticalLayout();
        searchForm.setWidthFull();
        searchForm.setSpacing(false);
        searchForm.addClassNames(LumoUtility.Gap.SMALL);
        // Add a class so CSS can target padding/margins consistently
        searchForm.addClassName("search-form");

        // Add padding to the form container on mobile so it doesn't touch edges
        searchForm.getStyle().set("padding-left", "clamp(0.5rem, 0vw, 0rem)");
        searchForm.getStyle().set("padding-right", "clamp(0.5rem, 0vw, 0rem)");

        productSearchField = new TextField("Product Name");
        productSearchField.setPlaceholder("Enter product name...");
        productSearchField.setWidthFull();
        // Make responsive
        productSearchField.getStyle().set("min-width", "200px").set("flex", "1 1 auto");

        // Trigger search on Enter key
        productSearchField.addKeyDownListener(Key.ENTER, e -> searchProducts());

        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> searchProducts());
        // Make button responsive
        searchButton.getStyle().set("min-width", "100px");

        HorizontalLayout productButtonRow = new HorizontalLayout(searchButton);
        productButtonRow.setPadding(false);
        productButtonRow.setSpacing(false);
        productButtonRow.setAlignItems(Alignment.CENTER);
        searchForm.add(productSearchField, productButtonRow);

        productSearchCountLabel = new Span();
        productSearchCountLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        productSearchCountLabel.setVisible(false);
        // Prevent wrapping so counts like "19 results" stay on a single line
        productSearchCountLabel.getStyle()
                .set("margin-bottom", "0.5rem")
                .set("white-space", "nowrap")
                .set("flex", "0 0 auto");
        // Ensure inline-block so nowrap and flex interaction behave consistently
        productSearchCountLabel.getStyle().set("display", "inline-block").set("min-width", "0");

        // Put the product search form and product count into a single row so the count is displayed on the right above the product search results grid.
        // Align to the end so the count lines up with the search button row.
        HorizontalLayout productTopRow = new HorizontalLayout(searchForm, productSearchCountLabel);
        productTopRow.setWidthFull();
        productTopRow.setAlignItems(Alignment.END);
        productTopRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        productTopRow.setSpacing(false);
        // Ensure the searchForm expands to take remaining width so the count label stays at the far right
        productTopRow.expand(searchForm);

        productSearchGrid = new Grid<>(ProductWithDateDTO.class, false);
        productSearchGrid.addClassName("data-query-grid");
        productSearchGrid.addClassName("desktop-only");
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
        // Max height is handled by CSS responsively (desktop only)

        // Create cards container for mobile
        productSearchCardsContainer = new VerticalLayout();
        productSearchCardsContainer.addClassName("mobile-only");
        productSearchCardsContainer.addClassName("cards-container");
        productSearchCardsContainer.setSpacing(true);
        productSearchCardsContainer.setPadding(false);
        productSearchCardsContainer.setVisible(false);
        // Add padding so cards don't touch edges
        productSearchCardsContainer.getStyle()
                .set("padding-left", "0.5rem")
                .set("padding-right", "0.5rem");

        layout.add(productTopRow, productSearchGrid, productSearchCardsContainer);
        return layout;
    }

    private void loadAllEntries() {
        try {
            List<FddbDataDTO> entries = fddbDataClient.getAllEntries();
            allEntriesGrid.setVisible(true); // Show grid when data is loaded
            allEntriesGrid.setItems(entries);
            // Always adjust height dynamically - shows all rows up to max viewport height
            allEntriesGrid.setAllRowsVisible(true);

            // Update count label
            // Use a non-breaking space so the number and label don't wrap on two lines
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
                // Always adjust height dynamically - shows all rows up to max viewport height
                dateProductsGrid.setAllRowsVisible(true);

                // Update count label (use non-breaking space to avoid wrapping)
                dateProductsCountLabel.setText(data.getProducts().size() + "\u00A0products");
                dateProductsCountLabel.setVisible(true);

                // Populate cards for mobile
                dateProductsCardsContainer.removeAll();
                dateProductsCardsContainer.setVisible(true);
                data.getProducts().forEach(product -> {
                    VerticalLayout card = createProductCard(product);
                    dateProductsCardsContainer.add(card);
                });

                showSuccess("Found " + data.getProducts().size() + " products for " + date);
            } else {
                dateProductsGrid.setItems();
                dateProductsGrid.setAllRowsVisible(true);
                dateProductsCountLabel.setVisible(false);
                dateProductsCardsContainer.removeAll();
                dateProductsCardsContainer.setVisible(false);
                showError("No data found for " + date);
            }
        } catch (ApiException e) {
            showError(e.getMessage());
            dateProductsGrid.setVisible(true);
            dateProductsGrid.setItems();
            dateProductsGrid.setAllRowsVisible(true);
            dateProductsCountLabel.setVisible(false);
            dateProductsCardsContainer.removeAll();
            dateProductsCardsContainer.setVisible(false);
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

            // Update count label (use non-breaking space to avoid wrapping)
            productSearchCountLabel.setText(products.size() + "\u00A0results");
            productSearchCountLabel.setVisible(true);

            // Populate cards for mobile
            productSearchCardsContainer.removeAll();
            productSearchCardsContainer.setVisible(true);
            products.forEach(productWithDate -> {
                VerticalLayout card = createProductWithDateCard(productWithDate);
                productSearchCardsContainer.add(card);
            });

            showSuccess("Found " + products.size() + " matching products");
        } catch (ApiException e) {
            showError(e.getMessage());
            productSearchGrid.setVisible(true);
            productSearchGrid.setItems();
            productSearchGrid.setAllRowsVisible(true);
            productSearchCountLabel.setVisible(false);
            productSearchCardsContainer.removeAll();
            productSearchCardsContainer.setVisible(false);
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

    /**
     * Create a card for a product item (mobile view)
     * Modern design with pill-style macros and emoji icons
     */
    private VerticalLayout createProductCard(ProductDTO product) {
        VerticalLayout card = new VerticalLayout();
        card.addClassName("mobile-data-card");
        card.setPadding(false);
        card.setSpacing(false);
        card.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "12px")
                .set("margin-bottom", "0.5rem")
                .set("padding", "12px")
                .set("box-shadow", "0 1px 3px rgba(0, 0, 0, 0.1)")
                .set("cursor", "pointer")
                .set("transition", "all 0.2s ease");

        // Make entire card clickable if link exists
        if (product.getLink() != null && !product.getLink().isEmpty()) {
            String fullLink = product.getLink().startsWith("http") ? product.getLink() : fddbLinkPrefix + product.getLink();
            card.addClickListener(e -> {
                card.getUI().ifPresent(ui -> ui.getPage().open(fullLink, "_blank"));
            });
            card.getStyle()
                    .set("cursor", "pointer");
        }

        // Product name and amount (left side)
        VerticalLayout headerSection = new VerticalLayout();
        headerSection.setPadding(false);
        headerSection.setSpacing(false);
        // Ensure header content is left-aligned (prevents centering on narrow viewports)
        headerSection.setAlignItems(FlexComponent.Alignment.START);
        headerSection.getStyle().set("margin-bottom", "12px");

        Paragraph nameText = new Paragraph(product.getName() != null ? product.getName() : "");
        nameText.getStyle()
                .set("font-weight", "600")
                .set("font-size", "16px")
                .set("color", "var(--lumo-body-text-color)")
                .set("margin", "0 0 4px 0")
                .set("line-height", "1.4")
                .set("word-wrap", "break-word");

        headerSection.add(nameText);

        if (product.getAmount() != null && !product.getAmount().isEmpty()) {
            Paragraph amountText = new Paragraph(product.getAmount());
            amountText.getStyle()
                    .set("font-size", "13px")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("margin", "0");
            headerSection.add(amountText);
        }

        card.add(headerSection);

        // Nutrition pills - centered horizontal wrap layout
        Div nutritionPills = new Div();
        nutritionPills.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "8px")  // Increased from 6px for better spacing with larger pills
                .set("justify-content", "center")
                .set("margin-top", "8px");

        nutritionPills.add(
                createMacroPill("🔥", "Cal", formatNumber(product.getCalories()), "", "#ef5350"),
                createMacroPill("🧈", "Fat", formatNumber(product.getFat()), "g", "#ffa726"),
                createMacroPill("🍞", "Carb", formatNumber(product.getCarbs()), "g", "#66bb6a"),
                createMacroPill("🥩", "Prot", formatNumber(product.getProtein()), "g", "#ab47bc")
        );

        card.add(nutritionPills);

        return card;
    }

    /**
     * Create a card for a product with date item (mobile view)
     * Modern design with date next to amount, pill-style macros with emoji icons
     */
    private VerticalLayout createProductWithDateCard(ProductWithDateDTO productWithDate) {
        VerticalLayout card = new VerticalLayout();
        card.addClassName("mobile-data-card");
        card.setPadding(false);
        card.setSpacing(false);
        card.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "12px")
                .set("margin-bottom", "0.5rem")
                .set("padding", "12px")
                .set("box-shadow", "0 1px 3px rgba(0, 0, 0, 0.1)")
                .set("transition", "all 0.2s ease");

        ProductDTO product = productWithDate.getProduct();
        if (product == null) {
            return card;
        }

        // Make entire card clickable if link exists
        if (product.getLink() != null && !product.getLink().isEmpty()) {
            String fullLink = product.getLink().startsWith("http") ? product.getLink() : fddbLinkPrefix + product.getLink();
            card.addClickListener(e -> {
                card.getUI().ifPresent(ui -> ui.getPage().open(fullLink, "_blank"));
            });
            card.getStyle()
                    .set("cursor", "pointer");
        }

        // Header section - Product name and amount/date on same line
        VerticalLayout headerSection = new VerticalLayout();
        headerSection.setPadding(false);
        headerSection.setSpacing(false);
        // Ensure header content is left-aligned (prevents centering on narrow viewports)
        headerSection.setAlignItems(FlexComponent.Alignment.START);
        headerSection.getStyle().set("margin-bottom", "12px");

        // Product name
        Paragraph nameText = new Paragraph(product.getName() != null ? product.getName() : "");
        nameText.getStyle()
                .set("font-weight", "600")
                .set("font-size", "16px")
                .set("color", "var(--lumo-body-text-color)")
                .set("margin", "0 0 4px 0")
                .set("line-height", "1.4")
                .set("word-wrap", "break-word");

        headerSection.add(nameText);

        // Amount and date on the same horizontal line
        HorizontalLayout amountDateRow = new HorizontalLayout();
        amountDateRow.setPadding(false);
        amountDateRow.setSpacing(true);
        amountDateRow.getStyle()
                .set("gap", "8px")
                .set("align-items", "center");
        // Prevent the amount and date from wrapping to the next line on narrow (portrait) viewports
        amountDateRow.getStyle().set("flex-wrap", "nowrap").set("white-space", "nowrap");
        // add class for CSS targeting
        amountDateRow.addClassName("amount-date-row");

        if (product.getAmount() != null && !product.getAmount().isEmpty()) {
            Span amountText = new Span(product.getAmount());
            amountText.addClassName("product-amount");
            amountText.getStyle()
                    .set("font-size", "13px")
                    .set("color", "var(--lumo-secondary-text-color)");
            amountDateRow.add(amountText);
        }

        // Date badge next to amount
        if (productWithDate.getDate() != null) {
            Div dateBadge = new Div();
            // add date badge class so CSS can style it inline next to amount
            dateBadge.addClassName("date-badge");
            dateBadge.addClassName("product-date-badge");
            Span dateText = new Span(productWithDate.getDate().toString());
            dateText.getStyle()
                    .set("font-size", "11px")
                    .set("color", "var(--lumo-body-text-color)")
                    .set("white-space", "nowrap");

            dateBadge.add(dateText);
            dateBadge.getStyle()
                    .set("padding", "4px 8px")
                    .set("background", "var(--lumo-contrast-5pct)")
                    .set("border", "1px solid var(--lumo-contrast-10pct)")
                    .set("border-radius", "8px");

            amountDateRow.add(dateBadge);
        }

        headerSection.add(amountDateRow);
        card.add(headerSection);

        // Nutrition pills - centered horizontal wrap layout
        Div nutritionPills = new Div();
        nutritionPills.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "8px")
                .set("justify-content", "center")
                .set("margin-top", "8px");

        nutritionPills.add(
                createMacroPill("🔥", "Cal", formatNumber(product.getCalories()), "", "#ef5350"),
                createMacroPill("🧈", "Fat", formatNumber(product.getFat()), "g", "#ffa726"),
                createMacroPill("🍞", "Carb", formatNumber(product.getCarbs()), "g", "#66bb6a"),
                createMacroPill("🥩", "Prot", formatNumber(product.getProtein()), "g", "#ab47bc")
        );

        card.add(nutritionPills);

        return card;
    }

    /**
     * Create a macro pill with emoji icon (matches Flutter app chip design)
     */
    private Div createMacroPill(String emoji, String label, String value, String unit, String color) {
        Div pill = new Div();
        pill.getStyle()
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("gap", "5px")
                .set("padding", "6px 12px")  // Increased from 4px 8px
                .set("background", hexToRgba(color, 0.08))
                .set("border", "0.8px solid " + hexToRgba(color, 0.25))
                .set("border-radius", "20px")
                .set("font-size", "13px");  // Increased from 11px

        // Emoji icon
        Span emojiSpan = new Span(emoji);
        emojiSpan.getStyle()
                .set("font-size", "16px")  // Increased from 12px
                .set("line-height", "1");

        // Label
        Span labelSpan = new Span(label + ":");
        labelSpan.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-weight", "400");

        // Value
        Span valueSpan = new Span(value + (unit.isEmpty() ? "" : " " + unit));
        valueSpan.getStyle()
                .set("color", color)
                .set("font-weight", "600");

        pill.add(emojiSpan, labelSpan, valueSpan);

        return pill;
    }

    /**
     * Convert hex color to rgba with opacity
     */
    private String hexToRgba(String hex, double opacity) {
        // Remove # if present
        hex = hex.replace("#", "");

        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);

        return String.format("rgba(%d, %d, %d, %.2f)", r, g, b, opacity);
    }
}

