package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Key;
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

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.applyResponsivePadding;
import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.createDatePickerI18n;

@Route(value = "correlation", layout = MainLayout.class)
@PageTitle("Correlation Analysis | FDDB Exporter")
public class CorrelationView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final CorrelationClient correlationClient;

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
        applyResponsivePadding(this);

        add(new H2("Correlation Analysis"));
        add(new Paragraph("Analyze correlations between product consumption and specific events/dates."));
        add(createInputForm());
        add(createResultSection());
    }

    private VerticalLayout createInputForm() {
        VerticalLayout form = new VerticalLayout();
        form.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);
        form.setSpacing(true);

        form.add(new H3("Input Parameters"));

        VerticalLayout inclusionSection = createKeywordSection("✓ Inclusion Keywords",
                "Products matching these keywords will be included",
                "rgba(63, 144, 140, 0.2)", "#3f908c", true);

        VerticalLayout exclusionSection = createKeywordSection("✗ Exclusion Keywords",
                "Products matching these keywords will be excluded",
                "rgba(154, 75, 85, 0.2)", "#9a4b55", false);

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
        startDatePicker.setValue(LocalDate.now().minusMonths(3));
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

    private VerticalLayout createKeywordSection(String title, String helpText, String bgColor, String titleColor, boolean isInclusion) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.getStyle()
                .set("padding", "0.75rem")
                .set("border-radius", "8px")
                .set("background", bgColor)
                .set("border", "1px solid " + bgColor.replace("0.08", "0.2"));

        H4 sectionTitle = new H4(title);
        sectionTitle.getStyle().set("margin", "0").set("color", titleColor);

        Paragraph help = new Paragraph(helpText);
        help.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        help.getStyle().set("margin", "0.25rem 0 0.5rem 0");

        TextField keywordInput = isInclusion ? (inclusionKeywordInput = new TextField()) : (exclusionKeywordInput = new TextField());
        keywordInput.setPlaceholder("Type keyword and press Enter...");
        keywordInput.setWidthFull();
        keywordInput.setClearButtonVisible(true);

        Button addBtn = new Button("+");
        addBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        addBtn.getStyle().set("min-width", "36px");
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
        inputRow.setPadding(false);
        inputRow.setSpacing(false);
        inputRow.setWidthFull();
        inputRow.getStyle().set("align-items", "center");

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
        pill.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER,
                LumoUtility.Padding.Horizontal.SMALL, LumoUtility.Padding.Vertical.XSMALL,
                LumoUtility.BorderRadius.LARGE);

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
                .set("gap", "0.5rem")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("white-space", "nowrap");

        Span label = new Span(text);

        Button removeBtn = new Button(new Icon(VaadinIcon.CLOSE_SMALL));
        removeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
        removeBtn.getStyle().set("min-width", "0").set("padding", "0").set("margin", "0").set("color", "inherit");
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
            input.setInclusionKeywords(inclusionKeywords.isEmpty() ? null : new java.util.ArrayList<>(inclusionKeywords));
            input.setExclusionKeywords(exclusionKeywords.isEmpty() ? null : new java.util.ArrayList<>(exclusionKeywords));
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
        resultDiv.removeAll();
        resultDiv.setVisible(true);
        resultDiv.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);

        content.add(new H3("Correlation Results"));

        HorizontalLayout summary = new HorizontalLayout();
        summary.addClassNames(LumoUtility.Gap.LARGE);
        summary.setWidthFull();
        summary.getStyle().set("flex-wrap", "wrap").set("justify-content", "center");

        Div matchedProductsCard = createSummaryCard("Matched Products", String.valueOf(result.getAmountMatchedProducts()));
        Div matchedDatesCard = createSummaryCard("Matched Dates", String.valueOf(result.getAmountMatchedDates()));

        summary.add(matchedProductsCard, matchedDatesCard);
        content.add(summary);

        if (result.getCorrelations() != null) {
            content.add(new H4("Correlation by Time Window"));

            HorizontalLayout correlationsLayout = new HorizontalLayout();
            correlationsLayout.setWidthFull();
            correlationsLayout.addClassNames(LumoUtility.Gap.MEDIUM);
            correlationsLayout.getStyle().set("flex-wrap", "wrap").set("justify-content", "center");

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

        if (result.getMatchedProducts() != null && !result.getMatchedProducts().isEmpty()) {
            content.add(new H4("Matched Products"));
            UnorderedList productList = new UnorderedList();
            result.getMatchedProducts().stream().limit(20).forEach(p -> productList.add(new ListItem(p)));
            if (result.getMatchedProducts().size() > 20) {
                productList.add(new ListItem("... and " + (result.getMatchedProducts().size() - 20) + " more"));
            }
            content.add(productList);
        }

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
        card.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.PRIMARY_10);
        card.getStyle().set("min-width", "140px").set("max-width", "200px").set("flex", "1 1 auto");

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
        card.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_10);
        card.getStyle().set("min-width", "140px").set("max-width", "180px").set("flex", "1 1 auto");

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

