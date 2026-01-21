package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationDetail;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationInputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationOutputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.Correlations;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.CorrelationClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.applyResponsivePadding;
import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.createDatePickerI18n;

@Route(value = "correlation", layout = MainLayout.class)
@PageTitle("Correlation Analysis | FDDB Exporter")
public class CorrelationView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final CorrelationClient correlationClient;
    private final dev.itobey.adapter.api.fddb.exporter.ui.service.StatsClient statsClient;
    private final ObjectMapper objectMapper;

    private CorrelationOutputDto lastCorrelationResult;
    private TextField inclusionKeywordInput;
    private FlexLayout inclusionKeywordsContainer;
    private List<String> inclusionKeywords = new java.util.ArrayList<>();

    private TextField exclusionKeywordInput;
    private FlexLayout exclusionKeywordsContainer;
    private List<String> exclusionKeywords = new java.util.ArrayList<>();

    private TextArea occurrenceDatesInput;
    private DatePicker startDatePicker;
    private VerticalLayout resultContainer;

    public CorrelationView(CorrelationClient correlationClient, dev.itobey.adapter.api.fddb.exporter.ui.service.StatsClient statsClient) {
        this.correlationClient = correlationClient;
        this.statsClient = statsClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        addClassName("correlation-view");
        setSpacing(true);
        setPadding(true);
        applyResponsivePadding(this);

        add(new H2("Correlation Analysis"));
        add(new Paragraph("Analyze correlations between product consumption and specific events/dates."));

        VerticalLayout contentWrapper = new VerticalLayout();
        contentWrapper.setPadding(false);
        contentWrapper.setSpacing(true);
        contentWrapper.add(createInputForm());
        contentWrapper.add(createResultSection());

        add(contentWrapper);
    }

    private VerticalLayout createInputForm() {
        VerticalLayout form = new VerticalLayout();
        form.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);
        form.setSpacing(true);

        form.add(new H3("Input Parameters"));

        VerticalLayout inclusionSection = createKeywordSection("✓ Inclusion Keywords",
                "Products matching these keywords will be included", true);

        VerticalLayout exclusionSection = createKeywordSection("✗ Exclusion Keywords",
                "Products matching these keywords will be excluded", false);

        VerticalLayout datesSection = new VerticalLayout();
        datesSection.setPadding(false);
        datesSection.setSpacing(true);
        datesSection.getStyle()
                .set("padding", "0.75rem")
                .set("border-radius", "8px")
                .set("background", "rgba(78, 97, 155, 0.08)")
                .set("border", "1px solid rgba(78, 97, 155, 0.2)");

        H4 datesTitle = new H4("📅 Occurrence Dates");
        datesTitle.getStyle().set("margin", "0");

        Paragraph datesHelp = new Paragraph("Enter dates when specific events occurred (comma-separated, YYYY-MM-DD format)");
        datesHelp.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        datesHelp.getStyle().set("margin", "0.25rem 0 0.5rem 0");

        occurrenceDatesInput = new TextArea();
        occurrenceDatesInput.setPlaceholder("e.g., 2024-01-15, 2024-02-20, 2024-03-10");
        occurrenceDatesInput.setWidthFull();
        occurrenceDatesInput.setHeight("100px");

        datesSection.add(datesTitle, datesHelp, occurrenceDatesInput);

        startDatePicker = new DatePicker("Start Date");
        // Try to get earliest entry date from stats, fallback to 3 months ago
        try {
            dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO stats = statsClient.getStats();
            if (stats != null && stats.getFirstEntryDate() != null) {
                startDatePicker.setValue(stats.getFirstEntryDate());
            } else {
                startDatePicker.setValue(LocalDate.now().minusMonths(3));
            }
        } catch (ApiException e) {
            startDatePicker.setValue(LocalDate.now().minusMonths(3));
        }
        startDatePicker.setHelperText("Start date for the analysis period");
        startDatePicker.setWidthFull();
        startDatePicker.setI18n(createDatePickerI18n());

        Button analyzeButton = new Button("Run Correlation Analysis");
        analyzeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        analyzeButton.setWidthFull();
        analyzeButton.addClickListener(e -> runCorrelation());
        analyzeButton.getStyle().set("margin-top", "1rem");

        form.add(inclusionSection, exclusionSection, datesSection, startDatePicker, analyzeButton);
        return form;
    }

    private VerticalLayout createKeywordSection(String title, String helpText, boolean isInclusion) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.getStyle()
                .set("padding", "0.75rem")
                .set("border-radius", "8px")
                .set("background", "rgba(78, 97, 155, 0.08)")
                .set("border", "1px solid rgba(78, 97, 155, 0.2)");

        H4 sectionTitle = new H4(title);
        sectionTitle.getStyle().set("margin", "0");

        Paragraph help = new Paragraph(helpText);
        help.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        help.getStyle().set("margin", "0.25rem 0 0.5rem 0");

        TextField keywordInput = isInclusion ? (inclusionKeywordInput = new TextField()) : (exclusionKeywordInput = new TextField());
        keywordInput.setPlaceholder("Type keyword and press Enter...");
        keywordInput.setWidthFull();
        keywordInput.setClearButtonVisible(true);
        keywordInput.getStyle()
                .set("flex", "1 1 auto")
                .set("min-width", "0");

        Button addBtn = new Button("Add");
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.getStyle()
                .set("flex", "0 0 auto")
                .set("white-space", "nowrap");
        addBtn.addClickListener(e -> {
            if (isInclusion) {
                addInclusionKeyword();
            } else {
                addExclusionKeyword();
            }
        });
        keywordInput.addKeyDownListener(Key.ENTER, e -> {
            if (isInclusion) {
                addInclusionKeyword();
            } else {
                addExclusionKeyword();
            }
        });

        HorizontalLayout inputRow = new HorizontalLayout(keywordInput, addBtn);
        inputRow.addClassName("keyword-input-row");
        inputRow.setPadding(false);
        inputRow.setSpacing(true);
        inputRow.setWidthFull();
        inputRow.setAlignItems(Alignment.END);
        inputRow.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "nowrap")
                .set("gap", "0.5rem")
                .set("align-items", "flex-end");

        FlexLayout keywordsContainer = isInclusion ? (inclusionKeywordsContainer = new FlexLayout()) : (exclusionKeywordsContainer = new FlexLayout());
        keywordsContainer.setWidthFull();
        keywordsContainer.getStyle().set("flex-wrap", "wrap").set("gap", "0.5rem").set("margin-top", "0.5rem");
        keywordsContainer.setVisible(false);

        section.add(sectionTitle, help, inputRow, keywordsContainer);
        return section;
    }

    private void addInclusionKeyword() {
        String keyword = inclusionKeywordInput.getValue().trim();
        if (!keyword.isEmpty() && !inclusionKeywords.contains(keyword)) {
            inclusionKeywords.add(keyword);
            inclusionKeywordsContainer.add(createPill(keyword, () -> removeInclusionKeyword(keyword), true));
            inclusionKeywordsContainer.setVisible(true);
            inclusionKeywordInput.clear();
        }
    }

    private void removeInclusionKeyword(String keyword) {
        inclusionKeywords.remove(keyword);
        refreshPills(inclusionKeywordsContainer, inclusionKeywords, this::removeInclusionKeyword, true);
        if (inclusionKeywords.isEmpty()) {
            inclusionKeywordsContainer.setVisible(false);
        }
    }

    private void addExclusionKeyword() {
        String keyword = exclusionKeywordInput.getValue().trim();
        if (!keyword.isEmpty() && !exclusionKeywords.contains(keyword)) {
            exclusionKeywords.add(keyword);
            exclusionKeywordsContainer.add(createPill(keyword, () -> removeExclusionKeyword(keyword), false));
            exclusionKeywordsContainer.setVisible(true);
            exclusionKeywordInput.clear();
        }
    }

    private void removeExclusionKeyword(String keyword) {
        exclusionKeywords.remove(keyword);
        refreshPills(exclusionKeywordsContainer, exclusionKeywords, this::removeExclusionKeyword, false);
        if (exclusionKeywords.isEmpty()) {
            exclusionKeywordsContainer.setVisible(false);
        }
    }

    private Div createPill(String text, Runnable onRemove, boolean isInclusion) {
        Div pill = new Div();
        pill.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER);

        if (isInclusion) {
            pill.getStyle()
                    .set("background-color", "#3f908c")
                    .set("color", "#ffffff");
        } else {
            pill.getStyle()
                    .set("background-color", "#9a4b55")
                    .set("color", "#ffffff");
        }

        pill.getStyle()
                .set("padding", "0.25rem 0.625rem")
                .set("border-radius", "16px")
                .set("gap", "0.375rem")
                .set("font-size", "0.875rem")
                .set("white-space", "nowrap")
                .set("height", "auto")
                .set("line-height", "1.2")
                .set("display", "inline-flex")
                .set("align-items", "center");

        Span label = new Span(text);
        label.getStyle()
                .set("line-height", "1.2")
                .set("padding", "0");

        Button removeBtn = new Button(new Icon(VaadinIcon.CLOSE_SMALL));
        removeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
        removeBtn.getStyle()
                .set("min-width", "0")
                .set("padding", "0")
                .set("margin", "0 0 0 -0.125rem")
                .set("color", "inherit")
                .set("height", "18px")
                .set("width", "18px")
                .set("cursor", "pointer");
        removeBtn.getElement().getStyle().set("--lumo-button-size", "18px");
        removeBtn.addClickListener(e -> onRemove.run());

        pill.add(label, removeBtn);
        return pill;
    }

    private void refreshPills(FlexLayout container, List<String> items, java.util.function.Consumer<String> remover, boolean isInclusion) {
        container.removeAll();
        items.forEach(item -> container.add(createPill(item, () -> remover.accept(item), isInclusion)));
    }

    private VerticalLayout createResultSection() {
        resultContainer = new VerticalLayout();
        resultContainer.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);
        resultContainer.setSpacing(true);
        resultContainer.setPadding(false);
        resultContainer.setWidthFull();
        resultContainer.setVisible(false);
        resultContainer.setDefaultHorizontalComponentAlignment(Alignment.START);

        return resultContainer;
    }

    private void runCorrelation() {
        if (inclusionKeywords.isEmpty()) {
            showError("Please add at least one inclusion keyword");
            return;
        }

        if (occurrenceDatesInput.getValue() == null || occurrenceDatesInput.getValue().trim().isEmpty()) {
            showError("Please enter occurrence dates");
            return;
        }

        try {
            String datesString = occurrenceDatesInput.getValue().trim();
            List<String> dates = Arrays.stream(datesString.split("[,\\n]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            if (dates.isEmpty()) {
                showError("No valid dates found");
                return;
            }

            CorrelationInputDto input = new CorrelationInputDto();
            input.setInclusionKeywords(new java.util.ArrayList<>(inclusionKeywords));
            input.setExclusionKeywords(exclusionKeywords.isEmpty() ? new java.util.ArrayList<>() : new java.util.ArrayList<>(exclusionKeywords));
            input.setOccurrenceDates(dates);
            input.setStartDate(startDatePicker.getValue() != null ? startDatePicker.getValue().format(DATE_FORMAT) : null);

            CorrelationOutputDto result = correlationClient.createCorrelation(input);
            displayResult(result);
            showSuccess("Correlation analysis completed");
        } catch (ApiException e) {
            showError("Error running correlation: " + e.getMessage());
        }
    }

    private void displayResult(CorrelationOutputDto result) {
        this.lastCorrelationResult = result;
        resultContainer.removeAll();
        resultContainer.setVisible(true);

        resultContainer.add(createResultHeader());

        Div summaryCards = dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.createCardsGrid("140px");
        summaryCards.add(
                createSummaryCard("Matched Products", String.valueOf(result.getAmountMatchedProducts())),
                createSummaryCard("Matched Dates", String.valueOf(result.getAmountMatchedDates()))
        );
        resultContainer.add(summaryCards);

        if (result.getCorrelations() != null) {
            resultContainer.add(new H4("Correlation by Time Window"));

            Div correlationsGrid = dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.createCardsGrid("140px");

            Correlations corr = result.getCorrelations();
            if (corr.getSameDay() != null) {
                correlationsGrid.add(createCorrelationCard("Same Day", corr.getSameDay()));
            }
            if (corr.getOneDayBefore() != null) {
                correlationsGrid.add(createCorrelationCard("1 Day Before", corr.getOneDayBefore()));
            }
            if (corr.getTwoDaysBefore() != null) {
                correlationsGrid.add(createCorrelationCard("2 Days Before", corr.getTwoDaysBefore()));
            }
            if (corr.getAcross2Days() != null) {
                correlationsGrid.add(createCorrelationCard("Across 2 Days", corr.getAcross2Days()));
            }
            if (corr.getAcross3Days() != null) {
                correlationsGrid.add(createCorrelationCard("Across 3 Days", corr.getAcross3Days()));
            }

            resultContainer.add(correlationsGrid);
        }

        if (result.getMatchedProducts() != null && !result.getMatchedProducts().isEmpty()) {
            resultContainer.add(new H4("Matched Products"));
            UnorderedList productList = new UnorderedList();
            result.getMatchedProducts().stream().limit(20).forEach(p -> productList.add(new ListItem(p)));
            if (result.getMatchedProducts().size() > 20) {
                productList.add(new ListItem("... and " + (result.getMatchedProducts().size() - 20) + " more"));
            }
            resultContainer.add(productList);
        }

        if (result.getMatchedDates() != null && !result.getMatchedDates().isEmpty()) {
            resultContainer.add(new H4("Matched Dates"));
            UnorderedList dateList = new UnorderedList();
            result.getMatchedDates().stream().limit(20).forEach(d -> dateList.add(new ListItem(d.toString())));
            if (result.getMatchedDates().size() > 20) {
                dateList.add(new ListItem("... and " + (result.getMatchedDates().size() - 20) + " more"));
            }
            resultContainer.add(dateList);
        }
    }

    private Div createSummaryCard(String title, String value) {
        return (Div) dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.createStatCard(title, value, "");
    }

    private Div createCorrelationCard(String timeWindow, CorrelationDetail detail) {
        Div card = dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.createCard();
        card.addClassNames(LumoUtility.Padding.MEDIUM);
        card.getStyle().set("min-width", "100px");

        Span windowSpan = new Span(timeWindow);
        windowSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD);

        Span percentageSpan = new Span(String.format("%.1f%%", detail.getPercentage()));
        percentageSpan.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.BOLD);

        if (detail.getPercentage() >= 70) {
            percentageSpan.getStyle().set("color", "#3f908c");
        } else if (detail.getPercentage() >= 40) {
            percentageSpan.addClassNames(LumoUtility.TextColor.WARNING);
        }

        Span daysSpan = new Span(detail.getMatchedDays() + " matched days");
        daysSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);

        VerticalLayout layout = new VerticalLayout(windowSpan, percentageSpan, daysSpan);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        card.add(layout);
        return card;
    }

    private Div createResultHeader() {
        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("flex-direction", "row")
                .set("flex-wrap", "nowrap")
                .set("align-items", "center")
                .set("justify-content", "flex-start")
                .set("gap", "0.5rem")
                .set("width", "100%");

        H3 title = new H3("Correlation Results");
        title.getStyle()
                .set("margin", "0")
                .set("white-space", "nowrap");

        Button copyButton = createCopyButton();

        Div buttonWrapper = new Div();
        buttonWrapper.getStyle()
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("justify-content", "flex-start")
                .set("width", "auto");
        buttonWrapper.add(copyButton);

        header.add(title, buttonWrapper);

        return header;
    }

    private Button createCopyButton() {
        Button copyButton = new Button("📋");
        copyButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        copyButton.setTooltipText("Copy results as JSON");
        copyButton.addClickListener(e -> copyResultsToClipboard());
        copyButton.getStyle().set("cursor", "pointer");
        return copyButton;
    }

    private void copyResultsToClipboard() {
        if (lastCorrelationResult == null) {
            showError("No results to copy");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(lastCorrelationResult);
            getElement().executeJs(
                    "navigator.clipboard.writeText($0).then(() => {}, (err) => { console.error('Failed to copy: ', err); });",
                    json
            );
            showSuccess("Results copied to clipboard");
        } catch (Exception e) {
            showError("Failed to copy results: " + e.getMessage());
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

