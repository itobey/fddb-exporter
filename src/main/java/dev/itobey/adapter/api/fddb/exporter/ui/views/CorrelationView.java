package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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

/**
 * View for correlation analysis.
 */
@Route(value = "correlation", layout = MainLayout.class)
@PageTitle("Correlation Analysis | FDDB Exporter")
public class CorrelationView extends VerticalLayout {

    private final CorrelationClient correlationClient;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TextField inclusionKeywordInput;
    private FlexLayout inclusionKeywordsContainer;
    private List<String> inclusionKeywords = new java.util.ArrayList<>();

    private TextField exclusionKeywordInput;
    private FlexLayout exclusionKeywordsContainer;
    private List<String> exclusionKeywords = new java.util.ArrayList<>();

    private TextArea occurrenceDatesInput;

    private DatePicker startDatePicker;
    private Div resultDiv;

    public CorrelationView(CorrelationClient correlationClient) {
        this.correlationClient = correlationClient;

        addClassName("correlation-view");
        setSpacing(true);
        setPadding(true);
        // Responsive padding - minimum on mobile for spacing from edges
        getStyle().set("padding", "clamp(0.5rem, 2vw, 1.5rem)");

        add(new H2("Correlation Analysis"));
        add(new Paragraph("Analyze correlations between product consumption and specific events/dates."));

        add(createInputForm());
        add(createResultSection());
    }

    private VerticalLayout createInputForm() {
        VerticalLayout form = new VerticalLayout();
        form.addClassNames(
                LumoUtility.Padding.LARGE,
                LumoUtility.BorderRadius.LARGE,
                LumoUtility.Background.CONTRAST_5
        );
        form.setSpacing(true);
        form.getStyle()
                .set("box-shadow", "0 2px 8px rgba(0, 0, 0, 0.1)");

        form.add(new H3("Input Parameters"));

        // Inclusion keywords section
        VerticalLayout inclusionSection = new VerticalLayout();
        inclusionSection.setPadding(false);
        inclusionSection.setSpacing(true);

        H4 inclusionTitle = new H4("Inclusion Keywords");
        inclusionTitle.getStyle().set("margin", "0");

        Paragraph inclusionHelp = new Paragraph("Products matching these keywords will be included in the analysis");
        inclusionHelp.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        inclusionHelp.getStyle().set("margin", "0.25rem 0 0.5rem 0");

        inclusionKeywordInput = new TextField();
        inclusionKeywordInput.setPlaceholder("Enter keyword and press Enter...");
        inclusionKeywordInput.setWidthFull();
        inclusionKeywordInput.addKeyPressListener(event -> {
            if (event.getKey().getKeys().get(0).equals("Enter")) {
                addInclusionKeyword();
            }
        });

        inclusionKeywordsContainer = new FlexLayout();
        inclusionKeywordsContainer.setWidthFull();
        inclusionKeywordsContainer.getStyle()
                .set("flex-wrap", "wrap")
                .set("gap", "0.5rem")
                .set("margin-top", "0.5rem");
        inclusionKeywordsContainer.setVisible(false);

        inclusionSection.add(inclusionTitle, inclusionHelp, inclusionKeywordInput, inclusionKeywordsContainer);

        // Exclusion keywords section
        VerticalLayout exclusionSection = new VerticalLayout();
        exclusionSection.setPadding(false);
        exclusionSection.setSpacing(true);

        H4 exclusionTitle = new H4("Exclusion Keywords");
        exclusionTitle.getStyle().set("margin", "0");

        Paragraph exclusionHelp = new Paragraph("Products matching these keywords will be excluded from the analysis");
        exclusionHelp.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        exclusionHelp.getStyle().set("margin", "0.25rem 0 0.5rem 0");

        exclusionKeywordInput = new TextField();
        exclusionKeywordInput.setPlaceholder("Enter keyword and press Enter...");
        exclusionKeywordInput.setWidthFull();
        exclusionKeywordInput.addKeyPressListener(event -> {
            if (event.getKey().getKeys().get(0).equals("Enter")) {
                addExclusionKeyword();
            }
        });

        exclusionKeywordsContainer = new FlexLayout();
        exclusionKeywordsContainer.setWidthFull();
        exclusionKeywordsContainer.getStyle()
                .set("flex-wrap", "wrap")
                .set("gap", "0.5rem")
                .set("margin-top", "0.5rem");
        exclusionKeywordsContainer.setVisible(false);

        exclusionSection.add(exclusionTitle, exclusionHelp, exclusionKeywordInput, exclusionKeywordsContainer);

        // Occurrence dates section
        VerticalLayout datesSection = new VerticalLayout();
        datesSection.setPadding(false);
        datesSection.setSpacing(true);

        H4 datesTitle = new H4("Occurrence Dates");
        datesTitle.getStyle().set("margin", "0");

        Paragraph datesHelp = new Paragraph("Dates when specific events occurred (comma-separated, YYYY-MM-DD format)");
        datesHelp.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        datesHelp.getStyle().set("margin", "0.25rem 0 0.5rem 0");

        occurrenceDatesInput = new TextArea();
        occurrenceDatesInput.setPlaceholder("Enter dates separated by commas (e.g., 2024-01-15, 2024-02-20, 2024-03-10)");
        occurrenceDatesInput.setWidthFull();
        occurrenceDatesInput.setHeight("120px");

        datesSection.add(datesTitle, datesHelp, occurrenceDatesInput);

        startDatePicker = new DatePicker("Start Date");
        startDatePicker.setValue(LocalDate.now().minusMonths(3));
        startDatePicker.setHelperText("Start date for the analysis period");
        startDatePicker.setWidthFull();

        Button analyzeButton = new Button("Run Correlation Analysis");
        analyzeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        analyzeButton.setWidthFull();
        analyzeButton.addClickListener(e -> runCorrelation());

        form.add(inclusionSection, exclusionSection, datesSection, startDatePicker, analyzeButton);
        return form;
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
        pill.addClassNames(
                LumoUtility.Display.FLEX,
                LumoUtility.AlignItems.CENTER,
                LumoUtility.Padding.Horizontal.SMALL,
                LumoUtility.Padding.Vertical.XSMALL,
                LumoUtility.BorderRadius.LARGE
        );

        // Apply green shade for inclusion, red shade for exclusion
        if (isInclusion) {
            pill.getStyle()
                    .set("background-color", "var(--lumo-success-color)")
                    .set("color", "var(--lumo-success-contrast-color)");
        } else {
            pill.getStyle()
                    .set("background-color", "var(--lumo-error-color)")
                    .set("color", "var(--lumo-error-contrast-color)");
        }

        pill.getStyle()
                .set("gap", "0.5rem")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("white-space", "nowrap");

        Span label = new Span(text);

        Button removeBtn = new Button(new Icon(VaadinIcon.CLOSE_SMALL));
        removeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
        removeBtn.getStyle()
                .set("min-width", "0")
                .set("padding", "0")
                .set("margin", "0")
                .set("color", "inherit");
        removeBtn.addClickListener(e -> onRemove.run());

        pill.add(label, removeBtn);
        return pill;
    }

    private void refreshPills(FlexLayout container, List<String> items, java.util.function.Consumer<String> remover, boolean isInclusion) {
        container.removeAll();
        items.forEach(item -> container.add(createPill(item, () -> remover.accept(item), isInclusion)));
    }

    private VerticalLayout createResultSection() {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(true);

        resultDiv = new Div();
        resultDiv.setWidthFull();
        resultDiv.setVisible(false);

        section.add(resultDiv);
        return section;
    }

    private void runCorrelation() {
        // Validate inputs
        if (inclusionKeywords.isEmpty()) {
            showError("Please enter at least one inclusion keyword");
            return;
        }

        // Parse occurrence dates from TextArea
        List<String> occurrenceDates = parseDates(occurrenceDatesInput.getValue());
        if (occurrenceDates.isEmpty()) {
            showError("Please enter at least one occurrence date");
            return;
        }

        if (startDatePicker.getValue() == null) {
            showError("Please select a start date");
            return;
        }

        try {
            CorrelationInputDto input = new CorrelationInputDto();
            input.setInclusionKeywords(new java.util.ArrayList<>(inclusionKeywords));
            input.setExclusionKeywords(new java.util.ArrayList<>(exclusionKeywords));
            input.setOccurrenceDates(new java.util.ArrayList<>(occurrenceDates));
            input.setStartDate(startDatePicker.getValue().format(DATE_FORMAT));

            CorrelationOutputDto result = correlationClient.createCorrelation(input);
            displayResult(result);
            showSuccess("Correlation analysis completed");
        } catch (ApiException e) {
            showError(e.getMessage());
        }
    }

    private List<String> parseDates(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(date -> {
                    try {
                        LocalDate.parse(date, DATE_FORMAT);
                        return true;
                    } catch (Exception e) {
                        showError("Invalid date format: " + date + ". Please use YYYY-MM-DD");
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    private void displayResult(CorrelationOutputDto result) {
        resultDiv.removeAll();
        resultDiv.setVisible(true);
        resultDiv.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.CONTRAST_5
        );

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);

        content.add(new H3("Correlation Results"));

        // Summary
        HorizontalLayout summary = new HorizontalLayout();
        summary.addClassNames(LumoUtility.Gap.LARGE);
        summary.setWidthFull();
        summary.getStyle().set("flex-wrap", "wrap").set("justify-content", "center");

        Div matchedProductsCard = createSummaryCard("Matched Products", String.valueOf(result.getAmountMatchedProducts()));
        Div matchedDatesCard = createSummaryCard("Matched Dates", String.valueOf(result.getAmountMatchedDates()));

        summary.add(matchedProductsCard, matchedDatesCard);
        content.add(summary);

        // Correlations by time window
        if (result.getCorrelations() != null) {
            content.add(new H4("Correlation by Time Window"));

            HorizontalLayout correlationsLayout = new HorizontalLayout();
            correlationsLayout.setWidthFull();
            correlationsLayout.addClassNames(LumoUtility.Gap.MEDIUM);
            correlationsLayout.getStyle()
                    .set("flex-wrap", "wrap")
                    .set("justify-content", "center");

            Correlations corr = result.getCorrelations();
            if (corr.getSameDay() != null) {
                correlationsLayout.add(createCorrelationCard("Same Day", corr.getSameDay()));
            }
            if (corr.getOneDayBefore() != null) {
                correlationsLayout.add(createCorrelationCard("1 Day Before", corr.getOneDayBefore()));
            }
            if (corr.getTwoDaysBefore() != null) {
                correlationsLayout.add(createCorrelationCard("2 Days Before", corr.getTwoDaysBefore()));
            }
            if (corr.getAcross2Days() != null) {
                correlationsLayout.add(createCorrelationCard("Across 2 Days", corr.getAcross2Days()));
            }
            if (corr.getAcross3Days() != null) {
                correlationsLayout.add(createCorrelationCard("Across 3 Days", corr.getAcross3Days()));
            }

            content.add(correlationsLayout);
        }

        // Matched products list
        if (result.getMatchedProducts() != null && !result.getMatchedProducts().isEmpty()) {
            content.add(new H4("Matched Products"));
            UnorderedList productList = new UnorderedList();
            result.getMatchedProducts().stream().limit(20).forEach(p -> productList.add(new ListItem(p)));
            if (result.getMatchedProducts().size() > 20) {
                productList.add(new ListItem("... and " + (result.getMatchedProducts().size() - 20) + " more"));
            }
            content.add(productList);
        }

        // Matched dates list
        if (result.getMatchedDates() != null && !result.getMatchedDates().isEmpty()) {
            content.add(new H4("Matched Dates"));
            UnorderedList dateList = new UnorderedList();
            result.getMatchedDates().stream().limit(20).forEach(d -> dateList.add(new ListItem(d.toString())));
            if (result.getMatchedDates().size() > 20) {
                dateList.add(new ListItem("... and " + (result.getMatchedDates().size() - 20) + " more"));
            }
            content.add(dateList);
        }

        resultDiv.add(content);
    }

    private Div createSummaryCard(String title, String value) {
        Div card = new Div();
        card.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.PRIMARY_10
        );
        // Responsive width
        card.getStyle()
                .set("min-width", "140px")
                .set("max-width", "200px")
                .set("flex", "1 1 auto");

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Span valueSpan = new Span(value);
        valueSpan.addClassNames(LumoUtility.FontSize.XXLARGE, LumoUtility.FontWeight.BOLD);

        VerticalLayout layout = new VerticalLayout(titleSpan, valueSpan);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setAlignItems(Alignment.CENTER);
        card.add(layout);
        return card;
    }

    private Div createCorrelationCard(String timeWindow, CorrelationDetail detail) {
        Div card = new Div();
        card.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.CONTRAST_10
        );
        // Responsive width
        card.getStyle()
                .set("min-width", "140px")
                .set("max-width", "180px")
                .set("flex", "1 1 auto");

        Span windowSpan = new Span(timeWindow);
        windowSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD);

        Span percentageSpan = new Span(String.format("%.1f%%", detail.getPercentage()));
        percentageSpan.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.BOLD);

        // Color based on percentage
        if (detail.getPercentage() >= 70) {
            percentageSpan.addClassNames(LumoUtility.TextColor.SUCCESS);
        } else if (detail.getPercentage() >= 40) {
            percentageSpan.addClassNames(LumoUtility.TextColor.WARNING);
        }

        Span daysSpan = new Span(detail.getMatchedDays() + " matched days");
        daysSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);

        VerticalLayout layout = new VerticalLayout(windowSpan, percentageSpan, daysSpan);
        layout.setPadding(false);
        layout.setSpacing(false);
        card.add(layout);
        return card;
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

