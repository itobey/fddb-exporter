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

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

@Route(value = "query", layout = MainLayout.class)
@PageTitle("Data Query | FDDB Exporter")
public class DataQueryView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final FddbDataClient fddbDataClient;
    private final String fddbLinkPrefix;

    private Grid<FddbDataDTO> allEntriesGrid;
    private Span allEntriesCountLabel;

    private DatePicker searchDatePicker;
    private Div dateStatsCards;
    private Grid<ProductDTO> dateProductsGrid;
    private Span dateProductsCountLabel;

    private TextField productSearchField;
    private Grid<ProductWithDateDTO> productSearchGrid;
    private Span productSearchCountLabel;

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

        tabSheet.setSizeFull();
        tabSheet.add("All Entries", createAllEntriesTab());
        tabSheet.add("Search by Date", createDateSearchTab());
        tabSheet.add("Search Products", createProductSearchTab());

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
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        layout.addClassName("data-query-tab-layout");

        Button loadButton = new Button("Load All Entries");
        loadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loadButton.addClickListener(e -> loadAllEntries());

        allEntriesCountLabel = new Span();
        allEntriesCountLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        allEntriesCountLabel.setVisible(false);

        HorizontalLayout topRow = new HorizontalLayout(loadButton, allEntriesCountLabel);
        topRow.setWidthFull();
        topRow.setAlignItems(Alignment.CENTER);
        topRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

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

        layout.add(topRow, allEntriesGrid);
        return layout;
    }

    private VerticalLayout createDateSearchTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        layout.addClassName("data-query-tab-layout");

        searchDatePicker = new DatePicker("Select Date");
        searchDatePicker.setPlaceholder("Select date...");
        searchDatePicker.setValue(LocalDate.now().minusDays(1));
        searchDatePicker.setWidthFull();
        searchDatePicker.setI18n(createDatePickerI18n());

        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> searchByDate());

        dateProductsCountLabel = new Span();
        dateProductsCountLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        dateProductsCountLabel.setVisible(false);

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
        dateProductsGrid.getStyle()
                .set("width", "100%")
                .set("position", "relative")
                .set("z-index", "0")
                .set("margin-top", "1rem")
                .set("overflow", "visible");

        layout.add(topRow, dateStatsCards, dateProductsGrid);
        return layout;
    }

    private VerticalLayout createProductSearchTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        layout.addClassName("data-query-tab-layout");

        productSearchField = new TextField("Product Name");
        productSearchField.setPlaceholder("Enter product name...");
        productSearchField.setWidthFull();
        productSearchField.addKeyDownListener(Key.ENTER, e -> searchProducts());

        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> searchProducts());

        productSearchCountLabel = new Span();
        productSearchCountLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        productSearchCountLabel.setVisible(false);

        HorizontalLayout topRow = new HorizontalLayout(productSearchField, searchButton, productSearchCountLabel);
        topRow.setWidthFull();
        topRow.setAlignItems(Alignment.END);
        topRow.setFlexGrow(1, productSearchField);

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

        layout.add(topRow, productSearchGrid);
        return layout;
    }

    private <T> Grid<T> createGrid(Class<T> beanType) {
        Grid<T> grid = new Grid<>(beanType, false);
        grid.addClassName("data-query-grid");
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setVisible(false);
        grid.setAllRowsVisible(true);
        return grid;
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
        try {
            List<FddbDataDTO> entries = fddbDataClient.getAllEntries();
            allEntriesGrid.setVisible(true);
            allEntriesGrid.setItems(entries);
            allEntriesCountLabel.setText(entries.size() + " entries");
            allEntriesCountLabel.setVisible(true);
            showSuccess("Loaded " + entries.size() + " entries");
        } catch (ApiException e) {
            showError(e.getMessage());
            allEntriesGrid.setItems();
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
            dateProductsGrid.setVisible(true);
            if (data != null && data.getProducts() != null) {
                // Update stats cards
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

                dateProductsGrid.setItems(data.getProducts());
                dateProductsCountLabel.setText(data.getProducts().size() + " products");
                dateProductsCountLabel.setVisible(true);
                showSuccess("Found " + data.getProducts().size() + " products for " + date);
            } else {
                dateStatsCards.setVisible(false);
                dateProductsGrid.setItems();
                dateProductsCountLabel.setVisible(false);
                showError("No data found for " + date);
            }
        } catch (ApiException e) {
            showError(e.getMessage());
            dateStatsCards.setVisible(false);
            dateProductsGrid.setItems();
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
            productSearchGrid.setVisible(true);
            productSearchGrid.setItems(products);
            productSearchCountLabel.setText(products.size() + " results");
            productSearchCountLabel.setVisible(true);
            showSuccess("Found " + products.size() + " matching products");
        } catch (ApiException e) {
            showError(e.getMessage());
            productSearchGrid.setItems();
            productSearchCountLabel.setVisible(false);
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
