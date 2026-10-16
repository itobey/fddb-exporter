package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.FddbDataClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

@Route(value = "export", layout = MainLayout.class)
@PageTitle("Sync from FDDB | FDDB Exporter")
public class DataExportView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter CHIP_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter CHIP_FORMAT_WITH_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * How many date chips a result shows before the rest collapse behind a toggle. A 365-day
     * export must not turn its card into a wall that shoves the neighbouring cards off the row.
     */
    private static final int CHIP_PREVIEW_COUNT = 10;

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

        Div introduction = new Div(new H2("Sync from FDDB"),
                new Paragraph("Import diary entries from your FDDB account into this app's configured storage. Existing days are updated."));
        introduction.addClassName("export-introduction");

        Div sectionsLayout = new Div();
        sectionsLayout.setWidthFull();
        sectionsLayout.addClassName("export-sections-layout");

        sectionsLayout.add(createYesterdaySection(), createDaysBackSection(), createDateRangeSection());
        Div content = new Div(introduction, sectionsLayout);
        content.addClassName("export-content");
        add(content);
    }

    private Div createDateRangeSection() {
        Div section = createExportCard("Sync by Date Range",
                "Import diary entries for a specific date range.");

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
                new FormLayout.ResponsiveStep("360px", 2)
        );

        dateRangeResult = createResultContainer();

        Button exportButton = createExportButton("Sync Date Range", this::exportDateRange, dateRangeResult);

        section.add(form, exportButton, dateRangeResult);
        return section;
    }

    private Div createDaysBackSection() {
        Div section = createExportCard("Sync Recent Days",
                "Import diary entries for a number of recent days.");

        FormLayout form = new FormLayout();

        daysBackField = new IntegerField("Days to Sync");
        daysBackField.setValue(7);
        daysBackField.setMin(1);
        daysBackField.setMax(365);
        daysBackField.setStepButtonsVisible(true);

        includeTodayCheckbox = new Checkbox("Include Today");
        includeTodayCheckbox.setValue(false);

        form.add(daysBackField, includeTodayCheckbox);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        daysBackResult = createResultContainer();

        Button exportButton = createExportButton("Sync Recent Days", this::exportDaysBack, daysBackResult);

        section.add(form, exportButton, daysBackResult);
        return section;
    }

    private Div createYesterdaySection() {
        Div section = createExportCard("Sync Yesterday",
                "Import yesterday's diary entries from FDDB.");
        section.addClassName("export-card--quick");

        yesterdayResult = createResultContainer();

        Button exportButton = createExportButton("Sync Yesterday", this::exportYesterday, yesterdayResult);

        section.add(exportButton, yesterdayResult);
        return section;
    }

    private Div createExportCard(String title, String description) {
        Div card = new Div();
        card.addClassName("export-card");

        H3 heading = new H3(title);
        heading.addClassName("export-card__title");

        Paragraph desc = new Paragraph(description);
        desc.addClassName("export-card__desc");

        Div headingText = new Div(heading, desc);
        headingText.addClassName("export-card__heading-text");

        card.add(headingText);
        return card;
    }

    /**
     * The export runs on the request thread: the scrape takes roughly a second per day, and the
     * view is blocked for the whole of it. Without server push there is no way to report progress
     * while that happens, so the honest thing is to make the wait legible rather than to imply a
     * granularity that does not exist. Two things fire client-side, before the round trip even
     * starts: the button disables itself, and the card's ledger switches to a pending line.
     *
     * @param result the ledger belonging to this card, put into its pending state on click
     */
    private Button createExportButton(String label, Runnable action, Div result) {
        Button button = new Button(label);
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.addClassName("export-card__action");
        button.setDisableOnClick(true);
        button.addClickListener(e -> {
            try {
                action.run();
            } finally {
                button.setEnabled(true);
                // displayResult clears this on every path that reaches it, but a validation error
                // returns before it ever runs and would strand the pending line on screen.
                clearPending(result);
            }
        });
        // A native listener, so this paints during the request rather than after it. Attached to
        // the result element so it can reach both nodes without a server round trip.
        result.getElement().executeJs(
                "const ledger = this;"
                        + "$0.addEventListener('click', () => {"
                        + "  ledger.classList.add('export-result--pending');"
                        + "});",
                button.getElement());
        return button;
    }

    private Div createResultContainer() {
        Div result = new Div();
        result.addClassName("export-result");
        // The ledger is the export's actual output and it appears without moving focus, so it has
        // to announce itself. "polite" rather than "assertive": the run has already finished, and
        // the accompanying notification is the urgent channel.
        result.getElement().setAttribute("aria-live", "polite");
        // Deliberately not setVisible(false): an invisible element is inactive on the server, so
        // the JS that clears the pending state would be queued instead of running. The ledger
        // stays in the DOM and hides itself through CSS for as long as it holds nothing.
        return result;
    }

    /**
     * The pending class is added client-side, so the server's class list never holds it and
     * removing it there does nothing. It has to be dropped in the DOM, where it was added.
     */
    private void clearPending(Div result) {
        result.getElement().executeJs("this.classList.remove('export-result--pending');");
    }

    private void exportDateRange() {
        if (!validateDateRange(fromDate, toDate, false)) {
            clearPending(dateRangeResult);
            return;
        }

        try {
            DateRangeDTO dateRange = DateRangeDTO.builder()
                    .fromDate(fromDate.getValue().format(DATE_FORMAT))
                    .toDate(toDate.getValue().format(DATE_FORMAT))
                    .build();

            displayResult(dateRangeResult, fddbDataClient.exportForDateRange(dateRange));
        } catch (ApiException e) {
            showError(e.getMessage());
        }
    }

    private void exportDaysBack() {
        daysBackField.setInvalid(false);
        if (daysBackField.getValue() == null || daysBackField.getValue() < 1) {
            daysBackField.setErrorMessage("Enter at least 1 day to sync.");
            daysBackField.setInvalid(true);
            clearPending(daysBackResult);
            return;
        }

        try {
            displayResult(daysBackResult, fddbDataClient.exportForDaysBack(
                    daysBackField.getValue(),
                    includeTodayCheckbox.getValue()
            ));
        } catch (ApiException e) {
            showError(e.getMessage());
        }
    }

    private void exportYesterday() {
        try {
            displayResult(yesterdayResult, fddbDataClient.exportForDaysBack(1, false));
        } catch (ApiException e) {
            showError("Could not sync yesterday: " + e.getMessage());
        }
    }

    /**
     * Renders one export run as a ledger inside the card that triggered it: a status line stating
     * what happened and when, then the dates themselves as chips. Replaces any previous run.
     */
    private void displayResult(Div resultDiv, ExportResultDTO result) {
        resultDiv.removeAll();
        clearPending(resultDiv);

        List<String> successful = result.getSuccessfulDays();
        List<String> unsuccessful = result.getUnsuccessfulDays();

        boolean hasSuccess = successful != null && !successful.isEmpty();
        boolean hasFailures = unsuccessful != null && !unsuccessful.isEmpty();

        String finishedAt = LocalTime.now().format(TIME_FORMAT);

        if (!hasSuccess && !hasFailures) {
            resultDiv.add(createStatusLine("neutral", "No days returned", finishedAt));
            showSuccess("Sync finished — no days returned");
            return;
        }

        if (hasSuccess) {
            resultDiv.add(createStatusLine("ok", dayCount(successful.size()) + " synced", finishedAt));
            resultDiv.add(createChipList(successful, "export-chip--ok"));
        }

        if (hasFailures) {
            resultDiv.add(createStatusLine("failed", dayCount(unsuccessful.size()) + " failed",
                    hasSuccess ? null : finishedAt));
            resultDiv.add(createChipList(unsuccessful, "export-chip--failed"));
        }

        if (hasFailures) {
            showError(dayCount(unsuccessful.size()) + " could not be synced");
        } else {
            showSuccess(dayCount(successful.size()) + " synced");
        }
    }

    private String dayCount(int count) {
        return count + (count == 1 ? " day" : " days");
    }

    private Component createStatusLine(String state, String label, String timestamp) {
        Span dot = new Span();
        dot.addClassNames("export-result__dot", "export-result__dot--" + state);

        Span text = new Span(label);
        text.addClassName("export-result__label");

        Div line = new Div(dot, text);
        line.addClassName("export-result__status");

        if (timestamp != null) {
            Span time = new Span(timestamp);
            time.addClassName("export-result__time");
            line.add(time);
        }
        return line;
    }

    private Component createChipList(List<String> days, String chipModifier) {
        Div chips = new Div();
        chips.addClassName("export-result__chips");

        for (int i = 0; i < days.size(); i++) {
            Span chip = createChip(days.get(i), chipModifier);
            if (i >= CHIP_PREVIEW_COUNT) {
                chip.addClassName("export-chip--overflow");
                chip.setVisible(false);
            }
            chips.add(chip);
        }

        int hidden = days.size() - CHIP_PREVIEW_COUNT;
        if (hidden > 0) {
            Button toggle = new Button("Show " + hidden + " more");
            toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
            toggle.addClassName("export-result__more");
            toggle.addClickListener(e -> {
                boolean expanding = !chips.hasClassName("export-result__chips--expanded");
                chips.getChildren()
                        .filter(c -> c.getElement().getClassList().contains("export-chip--overflow"))
                        .forEach(c -> c.setVisible(expanding));
                chips.getElement().getClassList().set("export-result__chips--expanded", expanding);
                toggle.setText(expanding ? "Show fewer" : "Show " + hidden + " more");
            });
            chips.add(toggle);
        }

        return chips;
    }

    private Span createChip(String isoDate, String chipModifier) {
        Span chip = new Span(formatChipLabel(isoDate));
        chip.addClassNames("export-chip", chipModifier);
        chip.getElement().setAttribute("title", isoDate);
        return chip;
    }

    /**
     * "2026-08-06" reads as a key, not a day. The weekday is what makes a diary export scannable;
     * the year only earns its space once the date leaves the current one. The ISO string stays
     * available as the chip's tooltip.
     */
    private String formatChipLabel(String isoDate) {
        try {
            LocalDate date = LocalDate.parse(isoDate, DATE_FORMAT);
            DateTimeFormatter formatter = date.getYear() == LocalDate.now().getYear()
                    ? CHIP_FORMAT
                    : CHIP_FORMAT_WITH_YEAR;
            return date.format(formatter);
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }
}
