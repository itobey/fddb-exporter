package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

@Route(value = "export", layout = MainLayout.class)
@PageTitle("Data Export | FDDB Exporter")
public class DataExportView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final FddbDataClient fddbDataClient;

    private DatePicker fromDate;
    private DatePicker toDate;
    private Div dateRangeResult;

    private IntegerField daysBackField;
    private Checkbox includeTodayCheckbox;
    private Div daysBackResult;

    private Div yesterdayResult;

    public DataExportView(FddbDataClient fddbDataClient) {
        this.fddbDataClient = fddbDataClient;

        addClassName("data-export-view");
        setSpacing(true);
        setPadding(true);
        applyResponsivePadding(this);

        add(new H2("Data Export"));
        add(new Paragraph("Export FDDB data from your account to the database."));

        Div sectionsLayout = new Div();
        sectionsLayout.setWidthFull();
        sectionsLayout.addClassName("export-sections-layout");
        sectionsLayout.addClassNames(LumoUtility.Gap.MEDIUM);
        sectionsLayout.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "1rem")
                .set("align-items", "flex-start");

        VerticalLayout yesterdaySection = createYesterdaySection();
        VerticalLayout daysBackSection = createDaysBackSection();
        VerticalLayout dateRangeSection = createDateRangeSection();

        String boxFlex = "1 1 320px";
        yesterdaySection.getStyle().set("flex", boxFlex).set("min-width", "260px").set("box-sizing", "border-box");
        daysBackSection.getStyle().set("flex", boxFlex).set("min-width", "260px").set("box-sizing", "border-box");
        dateRangeSection.getStyle().set("flex", boxFlex).set("min-width", "260px").set("box-sizing", "border-box");
        yesterdaySection.setWidthFull();
        daysBackSection.setWidthFull();
        dateRangeSection.setWidthFull();

        sectionsLayout.add(yesterdaySection, daysBackSection, dateRangeSection);
        add(sectionsLayout);
    }

    private VerticalLayout createDateRangeSection() {
        VerticalLayout section = createSection(null);
        section.add(new H3("Export by Date Range"));
        section.add(new Paragraph("Export all data within a specified date range."));

        FormLayout form = new FormLayout();

        fromDate = new DatePicker("From Date");
        fromDate.setValue(LocalDate.now().minusDays(7));
        fromDate.setRequired(true);
        fromDate.setI18n(createDatePickerI18n());

        toDate = new DatePicker("To Date");
        toDate.setValue(LocalDate.now().minusDays(1));
        toDate.setRequired(true);
        toDate.setI18n(createDatePickerI18n());

        form.add(fromDate, toDate);
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
        VerticalLayout section = createSection(null);
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

    private VerticalLayout createYesterdaySection() {
        VerticalLayout section = createSection(null);
        section.add(new H3("Export Yesterday"));
        section.add(new Paragraph("Quickly export data for yesterday only."));

        Button exportButton = new Button("Export Yesterday");
        exportButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        exportButton.addClickListener(e -> exportYesterday());
        exportButton.setWidthFull();

        yesterdayResult = new Div();
        yesterdayResult.setVisible(false);

        section.add(exportButton, yesterdayResult);
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

    private void exportYesterday() {
        try {
            ExportResultDTO result = fddbDataClient.exportForDaysBack(1, false);
            displayResult(yesterdayResult, result);

            if (result.getSuccessfulDays() != null && !result.getSuccessfulDays().isEmpty()) {
                showSuccess("Yesterday exported successfully: " + result.getSuccessfulDays().get(0));
            } else if (result.getUnsuccessfulDays() != null && !result.getUnsuccessfulDays().isEmpty()) {
                showError("Failed to export yesterday: " + result.getUnsuccessfulDays().get(0));
            } else {
                showSuccess("Export completed");
            }
        } catch (ApiException e) {
            showError("Failed to export yesterday: " + e.getMessage());
        }
    }

    private void displayResult(Div resultDiv, ExportResultDTO result) {
        resultDiv.removeAll();
        resultDiv.setVisible(true);
        resultDiv.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.Margin.Top.MEDIUM);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

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
