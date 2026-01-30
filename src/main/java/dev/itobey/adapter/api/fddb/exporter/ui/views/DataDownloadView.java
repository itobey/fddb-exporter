package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.DownloadFormat;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.DataDownloadClient;

import java.time.LocalDate;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

/**
 * Vaadin view for downloading FDDB data in various formats.
 * Allows users to:
 * - Choose between all data or a specific date range
 * - Select download format (CSV or JSON)
 * - Include or exclude product details (daily totals only)
 */
@Route(value = "download", layout = MainLayout.class)
@PageTitle("Data Download | FDDB Exporter")
public class DataDownloadView extends VerticalLayout {

    private static final String DATE_RANGE_ALL = "All Data";
    private static final String DATE_RANGE_CUSTOM = "Custom Date Range";

    private final DataDownloadClient dataDownloadClient;

    private RadioButtonGroup<String> dateRangeSelection;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private Select<DownloadFormat> formatSelect;
    private Checkbox includeProductsCheckbox;
    private RadioButtonGroup<String> decimalSeparatorSelection;
    private VerticalLayout dateRangeFieldsContainer;
    private VerticalLayout decimalSeparatorContainer;

    public DataDownloadView(DataDownloadClient dataDownloadClient, FddbExporterProperties properties) {
        this.dataDownloadClient = dataDownloadClient;

        addClassName("data-download-view");
        setSpacing(true);
        setPadding(true);
        applyResponsivePadding(this);

        add(new H2("Data Download"));
        add(new Paragraph("Download your FDDB nutrition data in CSV or JSON format."));

        if (!isMongoDbEnabled(properties)) {
            add(createMongoDbDisabledWarning("Data Download"));
            return;
        }

        add(createDownloadForm());
    }

    private VerticalLayout createDownloadForm() {
        VerticalLayout section = createSection(null);
        section.add(new H3("Download Options"));

        // Date range selection (radio buttons)
        dateRangeSelection = new RadioButtonGroup<>();
        dateRangeSelection.setLabel("Date Range");
        dateRangeSelection.setItems(DATE_RANGE_ALL, DATE_RANGE_CUSTOM);
        dateRangeSelection.setValue(DATE_RANGE_ALL);
        dateRangeSelection.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
        dateRangeSelection.addValueChangeListener(e -> updateDateRangeFieldsVisibility());

        // Date pickers for custom range
        fromDatePicker = new DatePicker("From Date");
        fromDatePicker.setValue(LocalDate.now().minusMonths(1));
        fromDatePicker.setI18n(createDatePickerI18n());

        toDatePicker = new DatePicker("To Date");
        toDatePicker.setValue(LocalDate.now().minusDays(1));
        toDatePicker.setI18n(createDatePickerI18n());

        FormLayout dateForm = new FormLayout();
        dateForm.add(fromDatePicker, toDatePicker);
        dateForm.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );

        dateRangeFieldsContainer = new VerticalLayout();
        dateRangeFieldsContainer.setPadding(false);
        dateRangeFieldsContainer.setSpacing(true);
        dateRangeFieldsContainer.add(dateForm);
        dateRangeFieldsContainer.setVisible(false);

        // Format selection
        formatSelect = new Select<>();
        formatSelect.setLabel("Download Format");
        formatSelect.setItems(DownloadFormat.values());
        formatSelect.setValue(DownloadFormat.CSV);
        formatSelect.setItemLabelGenerator(format -> format.name() + " (" + format.getFileExtension() + ")");
        formatSelect.addValueChangeListener(e -> updateDecimalSeparatorVisibility());

        // Include products checkbox
        includeProductsCheckbox = new Checkbox("Include product details");
        includeProductsCheckbox.setValue(false);
        includeProductsCheckbox.setTooltipText("If checked, includes all consumed products for each day. If unchecked, only downloads daily totals (calories, fat, carbs, etc.)");

        // Decimal separator selection (only for CSV)
        decimalSeparatorSelection = new RadioButtonGroup<>();
        decimalSeparatorSelection.setLabel("Decimal Separator (CSV only)");
        decimalSeparatorSelection.setItems("comma", "dot");
        decimalSeparatorSelection.setValue("comma");
        // Horizontal layout for compact display
        decimalSeparatorSelection.getStyle().set("display", "flex").set("flex-direction", "row");

        decimalSeparatorContainer = new VerticalLayout();
        decimalSeparatorContainer.setPadding(false);
        decimalSeparatorContainer.setSpacing(true);
        decimalSeparatorContainer.add(decimalSeparatorSelection);
        decimalSeparatorContainer.setVisible(true); // Show by default since CSV is the initial format

        // Download button
        Button downloadButton = new Button("Download Data");
        downloadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        downloadButton.addClickListener(e -> triggerDownload());
        downloadButton.setWidthFull();

        // Layout
        FormLayout optionsForm = new FormLayout();
        optionsForm.add(formatSelect);
        optionsForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        HorizontalLayout checkboxLayout = new HorizontalLayout(includeProductsCheckbox);
        checkboxLayout.setAlignItems(Alignment.CENTER);
        checkboxLayout.setPadding(false);

        section.add(
                dateRangeSelection,
                dateRangeFieldsContainer,
                optionsForm,
                checkboxLayout,
                decimalSeparatorContainer,
                downloadButton
        );

        return section;
    }

    private void updateDateRangeFieldsVisibility() {
        boolean isCustomRange = DATE_RANGE_CUSTOM.equals(dateRangeSelection.getValue());
        dateRangeFieldsContainer.setVisible(isCustomRange);
    }

    private void updateDecimalSeparatorVisibility() {
        boolean isCsvFormat = DownloadFormat.CSV.equals(formatSelect.getValue());
        decimalSeparatorContainer.setVisible(isCsvFormat);
    }

    private void triggerDownload() {
        LocalDate fromDate = null;
        LocalDate toDate = null;

        if (DATE_RANGE_CUSTOM.equals(dateRangeSelection.getValue())) {
            fromDate = fromDatePicker.getValue();
            toDate = toDatePicker.getValue();

            // Validate dates
            if (fromDate == null || toDate == null) {
                showError("Please select both from and to dates.");
                return;
            }

            if (fromDate.isAfter(toDate)) {
                showError("From date cannot be after to date.");
                return;
            }
        }

        DownloadFormat format = formatSelect.getValue();
        boolean includeProducts = includeProductsCheckbox.getValue();
        String decimalSeparator = decimalSeparatorSelection.getValue();

        // Build the download URL
        String downloadUrl = dataDownloadClient.buildDownloadUrl(fromDate, toDate, format, includeProducts, decimalSeparator);

        // Trigger download using JavaScript to avoid navigation
        UI.getCurrent().getPage().executeJs(
                "const link = document.createElement('a');" +
                        "link.href = $0;" +
                        "link.download = '';" +
                        "document.body.appendChild(link);" +
                        "link.click();" +
                        "document.body.removeChild(link);",
                downloadUrl
        );

        showSuccess("Download started!");
    }
}



