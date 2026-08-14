package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.function.SerializableRunnable;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.HealthService;
import dev.itobey.adapter.api.fddb.exporter.ui.service.StatsClient;
import dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils;

import java.util.Map;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

@Route(value = "dashboard", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Dashboard | FDDB Exporter")
public class DashboardView extends VerticalLayout {

    private static final String HIGHLIGHT_COLOR = "var(--highlight)";
    private final StatsClient statsClient;
    private final HealthService healthService;

    public DashboardView(StatsClient statsClient, HealthService healthService) {
        this.statsClient = statsClient;
        this.healthService = healthService;

        addClassName("dashboard-view");
        setSpacing(true);
        setPadding(true);
        applyResponsivePadding(this);

        add(new H2("Dashboard"));
        loadHealthChecks();
        loadStats();
    }

    private void loadHealthChecks() {
        try {
            Map<String, HealthService.ComponentHealth> components = healthService.getHealthStatus();
            displayHealthStatus(components);
        } catch (Exception e) {
            showError("Failed to load health status: " + e.getMessage());
        }
    }

    private void displayHealthStatus(Map<String, HealthService.ComponentHealth> components) {
        add(new H3("System Health"));

        // Same auto-fit grid as the stats sections below: the cards sit in one dense row on a wide
        // screen and re-flow to fewer columns as the viewport narrows, all three staying present.
        // Below the phone breakpoint the grid drops to a single column and the cards turn into
        // ledger rows - see .health-grid in styles.css.
        Div healthCardsContainer = createCardsGrid("160px");
        healthCardsContainer.addClassName("health-grid");

        if (components.containsKey("fddb-login-check")) {
            HealthService.ComponentHealth fddbHealth = components.get("fddb-login-check");
            healthCardsContainer.add(createHealthCard("FDDB Connection", fddbHealth, "🌐"));
        }

        if (components.containsKey("mongodb")) {
            HealthService.ComponentHealth mongoHealth = components.get("mongodb");
            healthCardsContainer.add(createHealthCard("MongoDB", mongoHealth, "🍃"));
        }

        if (components.containsKey("influxdb")) {
            HealthService.ComponentHealth influxHealth = components.get("influxdb");
            healthCardsContainer.add(createHealthCard("InfluxDB", influxHealth, "📊"));
        }

        add(healthCardsContainer);
    }

    private Component createHealthCard(String name, HealthService.ComponentHealth health, String emoji) {
        Div card = ViewUtils.createCard();
        card.addClassNames(LumoUtility.Padding.LARGE, LumoUtility.BorderRadius.LARGE);

        String status = health.getStatus();
        // The border is a fill and the status label is text, so they take different steps of the
        // same identity - the fill tokens run 1.3:1 to 2.3:1 as text and none of them are readable.
        String borderColor;
        String statusTextColor;

        if ("UP".equalsIgnoreCase(status)) {
            borderColor = "var(--green-accent)";
            statusTextColor = "var(--green-accent-text)";
        } else if ("DISABLED".equalsIgnoreCase(status)) {
            borderColor = "var(--highlight)";
            statusTextColor = "var(--highlight-text)";
        } else {
            borderColor = "var(--red-accent)";
            statusTextColor = "var(--red-accent-text)";
        }

        card.getStyle()
                .set("min-width", "160px")
                .set("border-left", "4px solid " + borderColor);

        // Decorative - see the note in ViewUtils.createNutrientCard.
        // Placement (block, and the gap under it) lives in .health-card__glyph rather than inline,
        // so the phone breakpoint can put the glyph beside the text instead of above it.
        Span emojiSpan = new Span(emoji);
        emojiSpan.addClassNames(LumoUtility.FontSize.XXLARGE);
        emojiSpan.addClassName("health-card__glyph");
        emojiSpan.getElement().setAttribute("aria-hidden", "true");

        // .label-small is the theme's Label role and already sets size, weight, tracking and case.
        Span nameSpan = new Span(name);
        nameSpan.addClassName("label-small");

        // Only the colour is computed, so only the colour stays inline; centring and full width are
        // the class's job and would otherwise outrank the phone layout.
        Span statusSpan = new Span(status);
        statusSpan.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);
        statusSpan.addClassName("health-card__status");
        statusSpan.getStyle().set("color", statusTextColor);

        // The name, status and details are one group that stays together when the card turns
        // sideways on a phone; the glyph is the only thing that moves out beside them. Without this
        // wrapper the details would be siblings of the glyph and each would start its own row.
        Div textGroup = new Div(nameSpan, statusSpan);
        textGroup.addClassName("health-card__text");

        if (health.getDetails() != null && !health.getDetails().isEmpty()) {
            for (Map.Entry<String, Object> detail : health.getDetails().entrySet()) {
                if (!"error".equalsIgnoreCase(detail.getKey()) && detail.getValue() != null) {
                    String detailText = detail.getKey() + ": " + detail.getValue();
                    Span detailSpan = new Span(detailText);
                    detailSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
                    detailSpan.addClassName("health-card__detail");
                    textGroup.add(detailSpan);
                }
            }
        }

        VerticalLayout layout = new VerticalLayout(emojiSpan, textGroup);
        layout.addClassName("health-card__body");
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        card.add(layout);
        return card;
    }

    private void loadStats() {
        try {
            displayStats(statsClient.getStats());
        } catch (ApiException e) {
            showError("Failed to load statistics: " + e.getMessage());
        }
    }

    private void displayStats(StatsDTO stats) {
        add(new H3("Global Stats"));

        Div overviewCards = createCardsGrid("140px");
        overviewCards.add(
                createStatCard("Total Entries", String.valueOf(stats.getAmountEntries()), "days in database"),
                createStatCard("Entry Coverage", String.format("%.1f%%", stats.getEntryPercentage()), "of days tracked"),
                createStatCard("Unique Products", String.valueOf(stats.getUniqueProducts()), "different products"),
                createStatCard("Total Products", String.valueOf(stats.getTotalProducts()), "entries logged"),
                createStatCard("First Entry", stats.getFirstEntryDate() != null ? stats.getFirstEntryDate().toString() : "N/A", "start date"),
                createStatCard("Missing Day", stats.getMostRecentMissingDay() != null ? stats.getMostRecentMissingDay().toString() : "N/A", "most recent")
        );
        add(overviewCards);

        if (stats.getAverageTotals() != null) {
            add(new H3("Average Daily Nutrition"));
            Div averageCards = createCardsGrid("120px");
            StatsDTO.Averages avg = stats.getAverageTotals();
            averageCards.add(
                    createNutrientCard("Calories", formatNumber(avg.getAvgTotalCalories()), "kcal", "🔥", null),
                    createNutrientCard("Fat", formatNumber(avg.getAvgTotalFat()), "g", "🧈", null),
                    createNutrientCard("Carbs", formatNumber(avg.getAvgTotalCarbs()), "g", "🍞", null),
                    createNutrientCard("Sugar", formatNumber(avg.getAvgTotalSugar()), "g", "🍬", null),
                    createNutrientCard("Protein", formatNumber(avg.getAvgTotalProtein()), "g", "🥩", null),
                    createNutrientCard("Fibre", formatNumber(avg.getAvgTotalFibre()), "g", "🥦", null)
            );
            add(averageCards);
        }

        add(new H3("Highest Daily Values"));
        Div highestCards = createCardsGrid("160px");
        if (stats.getHighestCaloriesDay() != null) {
            highestCards.add(createHighestCard("Calories", stats.getHighestCaloriesDay(), "🔥", "kcal"));
        }
        if (stats.getHighestFatDay() != null) {
            highestCards.add(createHighestCard("Fat", stats.getHighestFatDay(), "🧈", "g"));
        }
        if (stats.getHighestCarbsDay() != null) {
            highestCards.add(createHighestCard("Carbs", stats.getHighestCarbsDay(), "🍞", "g"));
        }
        if (stats.getHighestSugarDay() != null) {
            highestCards.add(createHighestCard("Sugar", stats.getHighestSugarDay(), "🍬", "g"));
        }
        if (stats.getHighestProteinDay() != null) {
            highestCards.add(createHighestCard("Protein", stats.getHighestProteinDay(), "🥩", "g"));
        }
        if (stats.getHighestFibreDay() != null) {
            highestCards.add(createHighestCard("Fibre", stats.getHighestFibreDay(), "🥦", "g"));
        }
        add(highestCards);
    }

    private Component createHighestCard(String nutrient, StatsDTO.DayStats dayStats, String emoji, String unit) {
        Div card = ViewUtils.createCard("card--highlight");
        card.addClassNames(LumoUtility.Padding.LARGE, LumoUtility.BorderRadius.LARGE);
        card.getStyle()
                .set("min-width", "140px")
                // use HIGHLIGHT_COLOR for the left border
                .set("border-left", "4px solid " + HIGHLIGHT_COLOR);

        String dateText = dayStats.getDate() != null ? dayStats.getDate().toString() : "N/A";

        if (dayStats.getDate() != null) {
            SerializableRunnable showEntries = () -> UI.getCurrent().navigate("entries",
                    QueryParameters.simple(Map.of("date", dayStats.getDate().toString())));
            card.addClickListener(event -> showEntries.run());
            // The card's visible text is emoji plus bare numbers, so it needs an explicit label that
            // says what activating it does.
            makeAccessibleButton(card,
                    "Show entries for " + dateText + ", highest " + nutrient + " day with "
                            + formatNumber(dayStats.getTotal()) + " " + unit,
                    showEntries);
        }

        // Decorative - see the note in ViewUtils.createNutrientCard.
        Span emojiSpan = new Span(emoji);
        emojiSpan.addClassNames(LumoUtility.FontSize.XXLARGE);
        emojiSpan.getStyle().set("display", "block").set("margin-bottom", "0.5rem");
        emojiSpan.getElement().setAttribute("aria-hidden", "true");

        // As above: .label-small already carries the size, weight and secondary colour.
        Span nutrientSpan = new Span(nutrient);
        nutrientSpan.addClassName("label-small");

        // .card__value is the Readout role: a fluid clamp at weight 700. No fixed font-size utility
        // is applied alongside it, since a fixed size would fight the clamp.
        Span valueSpan = new Span(formatNumber(dayStats.getTotal()) + " " + unit);
        valueSpan.addClassName("card__value");
        // Centered, in the highlight's TEXT variant - the fill measures 2.33:1 and this is the
        // card's headline figure.
        valueSpan.getStyle().set("text-align", "center").set("width", "100%").set("color", "var(--highlight-text)");

        Div dateBadge = new Div();
        dateBadge.addClassName("date-badge");
        dateBadge.addClassNames(LumoUtility.Padding.Horizontal.SMALL, LumoUtility.Padding.Vertical.XSMALL, LumoUtility.FontSize.XSMALL);
        dateBadge.setText(dayStats.getDate() != null ? dayStats.getDate().toString() : "N/A");

        VerticalLayout layout = new VerticalLayout(emojiSpan, nutrientSpan, valueSpan, dateBadge);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        card.add(layout);
        return card;
    }
}
