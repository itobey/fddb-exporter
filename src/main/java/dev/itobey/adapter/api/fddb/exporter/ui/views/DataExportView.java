package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.FddbDataClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * View for exporting FDDB data.
 */
@Route(value = "export", layout = MainLayout.class)
@PageTitle("Data Export | FDDB Exporter")
public class DataExportView extends VerticalLayout {

    private final FddbDataClient fddbDataClient;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Date range export components
    private DatePicker fromDate;
    private DatePicker toDate;
    private Div dateRangeResult;

    // Days back export components
    private IntegerField daysBackField;
    private Checkbox includeTodayCheckbox;
    private Div daysBackResult;

    public DataExportView(FddbDataClient fddbDataClient) {
        this.fddbDataClient = fddbDataClient;

        addClassName("data-export-view");
        setSpacing(true);
        setPadding(true);
        // Responsive padding - minimum on mobile for spacing from edges
        getStyle().set("padding", "clamp(0.5rem, 2vw, 1.5rem)");

        add(new H2("Data Export"));
        add(new Paragraph("Export FDDB data from your account to the database."));

        // Create a horizontal layout for the two export sections
        HorizontalLayout sectionsLayout = new HorizontalLayout();
        sectionsLayout.setWidthFull();
        sectionsLayout.setSpacing(true);
        sectionsLayout.addClassNames(LumoUtility.Gap.MEDIUM);
        sectionsLayout.getStyle().set("flex-wrap", "wrap");
        sectionsLayout.getStyle().set("align-items", "stretch");

        // Date Range Export Section
        VerticalLayout dateRangeSection = createDateRangeSection();
        dateRangeSection.getStyle().set("flex", "1 1 400px"); // Flex-grow, flex-shrink, min-width

        // Days Back Export Section
        VerticalLayout daysBackSection = createDaysBackSection();
        daysBackSection.getStyle().set("flex", "1 1 400px"); // Flex-grow, flex-shrink, min-width

        sectionsLayout.add(dateRangeSection, daysBackSection);
        add(sectionsLayout);
    }

    private VerticalLayout createDateRangeSection() {
        VerticalLayout section = new VerticalLayout();
        section.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.CONTRAST_5
        );
        section.setSpacing(true);

        section.add(new H3("Export by Date Range"));
        section.add(new Paragraph("Export all data within a specified date range."));

        FormLayout form = new FormLayout();

        fromDate = new DatePicker("From Date");
        fromDate.setValue(LocalDate.now().minusDays(7));
        fromDate.setRequired(true);

        toDate = new DatePicker("To Date");
        toDate.setValue(LocalDate.now().minusDays(1)); // Set to yesterday
        toDate.setRequired(true);

        form.add(fromDate, toDate);
        // Responsive: 1 column on mobile, 2 on desktop
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );

        Button exportButton = new Button("Export Date Range");
        exportButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        exportButton.addClickListener(e -> exportDateRange());
        exportButton.setWidthFull();

        dateRangeResult = new Div();
        dateRangeResult.setVisible(false);

        section.add(form, exportButton, dateRangeResult);
        return section;
    }

    private VerticalLayout createDaysBackSection() {
        VerticalLayout section = new VerticalLayout();
        section.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.CONTRAST_5
        );
        section.setSpacing(true);

        section.add(new H3("Export Recent Days"));
        section.add(new Paragraph("Export data for a number of recent days."));

        FormLayout form = new FormLayout();

        daysBackField = new IntegerField("Days to Export");
        daysBackField.setValue(7);
        daysBackField.setMin(1);
        daysBackField.setMax(365);
        daysBackField.setStepButtonsVisible(true);

        includeTodayCheckbox = new Checkbox("Include Today");
        includeTodayCheckbox.setValue(false);

        form.add(daysBackField, includeTodayCheckbox);
        // Responsive: 1 column on mobile, 2 on desktop
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );

        Button exportButton = new Button("Export Recent Days");
        exportButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        exportButton.addClickListener(e -> exportDaysBack());
        exportButton.setWidthFull();

        daysBackResult = new Div();
        daysBackResult.setVisible(false);

        section.add(form, exportButton, daysBackResult);
        return section;
    }

    private void exportDateRange() {
        if (fromDate.getValue() == null || toDate.getValue() == null) {
            showError("Please select both from and to dates");
            return;
        }

        if (fromDate.getValue().isAfter(toDate.getValue())) {
            showError("From date must be before or equal to to date");
            return;
        }

        try {
            DateRangeDTO dateRange = DateRangeDTO.builder()
                    .fromDate(fromDate.getValue().format(DATE_FORMAT))
                    .toDate(toDate.getValue().format(DATE_FORMAT))
                    .build();

            ExportResultDTO result = fddbDataClient.exportForDateRange(dateRange);
            displayResult(dateRangeResult, result);
            showSuccess("Export completed successfully");
        } catch (ApiException e) {
            showError(e.getMessage());
        }
    }

    private void exportDaysBack() {
        if (daysBackField.getValue() == null || daysBackField.getValue() < 1) {
            showError("Please enter a valid number of days");
            return;
        }

        try {
            ExportResultDTO result = fddbDataClient.exportForDaysBack(
                    daysBackField.getValue(),
                    includeTodayCheckbox.getValue()
            );
            displayResult(daysBackResult, result);
            showSuccess("Export completed successfully");
        } catch (ApiException e) {
            showError(e.getMessage());
        }
    }

    private void displayResult(Div resultDiv, ExportResultDTO result) {
        resultDiv.removeAll();
        resultDiv.setVisible(true);
        resultDiv.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.Margin.Top.MEDIUM);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // Successful days
        if (result.getSuccessfulDays() != null && !result.getSuccessfulDays().isEmpty()) {
            Div successSection = new Div();
            successSection.addClassNames(LumoUtility.Padding.SMALL, LumoUtility.Background.SUCCESS_10, LumoUtility.BorderRadius.SMALL);

            Span successTitle = new Span("Successful: " + result.getSuccessfulDays().size() + " day(s)");
            successTitle.addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.TextColor.SUCCESS);

            UnorderedList successList = new UnorderedList();
            result.getSuccessfulDays().forEach(day -> successList.add(new ListItem(day)));

            successSection.add(successTitle, successList);
            content.add(successSection);
        }

        // Unsuccessful days
        if (result.getUnsuccessfulDays() != null && !result.getUnsuccessfulDays().isEmpty()) {
            Div failSection = new Div();
            failSection.addClassNames(LumoUtility.Padding.SMALL, LumoUtility.Background.ERROR_10, LumoUtility.BorderRadius.SMALL);

            Span failTitle = new Span("Unsuccessful: " + result.getUnsuccessfulDays().size() + " day(s)");
            failTitle.addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.TextColor.ERROR);

            UnorderedList failList = new UnorderedList();
            result.getUnsuccessfulDays().forEach(day -> failList.add(new ListItem(day)));

            failSection.add(failTitle, failList);
            content.add(failSection);
        }

        resultDiv.add(content);
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
