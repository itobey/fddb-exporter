package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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

    private static final String HIGHLIGHT_COLOR = "#ae9357";
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

        Div healthCardsContainer = new Div();
        healthCardsContainer.setWidthFull();
        healthCardsContainer.addClassName("health-cards-container");

        if (components.containsKey("fddb-login-check")) {
            HealthService.ComponentHealth fddbHealth = components.get("fddb-login-check");
            Div fddbCard = (Div) createHealthCard("FDDB Connection", fddbHealth, "🌐");
            fddbCard.addClassName("health-card-fddb");
            healthCardsContainer.add(fddbCard);
        }

        Div databaseCardsWrapper = new Div();
        databaseCardsWrapper.addClassName("database-cards-wrapper");

        if (components.containsKey("mongodb")) {
            HealthService.ComponentHealth mongoHealth = components.get("mongodb");
            Div mongoCard = (Div) createHealthCard("MongoDB", mongoHealth, "🍃");
            mongoCard.addClassName("health-card-database");
            databaseCardsWrapper.add(mongoCard);
        }

        if (components.containsKey("influxdb")) {
            HealthService.ComponentHealth influxHealth = components.get("influxdb");
            Div influxCard = (Div) createHealthCard("InfluxDB", influxHealth, "📊");
            influxCard.addClassName("health-card-database");
            databaseCardsWrapper.add(influxCard);
        }

        healthCardsContainer.add(databaseCardsWrapper);
        add(healthCardsContainer);
    }

    private Component createHealthCard(String name, HealthService.ComponentHealth health, String emoji) {
        Div card = ViewUtils.createCard();
        card.addClassNames(LumoUtility.Padding.LARGE, LumoUtility.BorderRadius.LARGE);

        String status = health.getStatus();
        String borderColor;

        if ("UP".equalsIgnoreCase(status)) {
            borderColor = "#3f908c";
        } else if ("DISABLED".equalsIgnoreCase(status)) {
            borderColor = "#ae9357";
        } else {
            borderColor = "#9a4b55";
        }

        card.getStyle()
                .set("min-width", "160px")
                .set("border-left", "4px solid " + borderColor);

        Span emojiSpan = new Span(emoji);
        emojiSpan.addClassNames(LumoUtility.FontSize.XXLARGE);
        emojiSpan.getStyle().set("display", "block").set("margin-bottom", "0.5rem");

        Span nameSpan = new Span(name);
        nameSpan.addClassNames(LumoUtility.FontSize.MEDIUM, LumoUtility.FontWeight.SEMIBOLD);
        nameSpan.addClassName("label-small");

        Span statusSpan = new Span(status);
        statusSpan.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);
        statusSpan.getStyle()
                .set("text-align", "center")
                .set("width", "100%")
                .set("color", borderColor);

        // Add additional details if available
        VerticalLayout layout = new VerticalLayout(emojiSpan, nameSpan, statusSpan);

        if (health.getDetails() != null && !health.getDetails().isEmpty()) {
            for (Map.Entry<String, Object> detail : health.getDetails().entrySet()) {
                if (!"error".equalsIgnoreCase(detail.getKey()) && detail.getValue() != null) {
                    String detailText = detail.getKey() + ": " + detail.getValue();
                    Span detailSpan = new Span(detailText);
                    detailSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
                    detailSpan.getStyle().set("text-align", "center");
                    layout.add(detailSpan);
                }
            }
        }

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

        card.addClickListener(event -> {
            if (dayStats.getDate() != null) {
                UI.getCurrent().navigate("query", QueryParameters.simple(Map.of("date", dayStats.getDate().toString())));
            }
        });

        Span emojiSpan = new Span(emoji);
        emojiSpan.addClassNames(LumoUtility.FontSize.XXLARGE);
        emojiSpan.getStyle().set("display", "block").set("margin-bottom", "0.5rem");

        Span nutrientSpan = new Span(nutrient);
        nutrientSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD, LumoUtility.TextColor.SECONDARY);
        nutrientSpan.addClassName("label-small");

        Span valueSpan = new Span(formatNumber(dayStats.getTotal()) + " " + unit);
        valueSpan.addClassNames(LumoUtility.FontSize.XXLARGE, LumoUtility.FontWeight.BOLD);
        valueSpan.addClassName("card__value");
        // set the value text color to the highlight color and center it
        valueSpan.getStyle().set("text-align", "center").set("width", "100%").set("color", HIGHLIGHT_COLOR);

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

    private void showError(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setDuration(5000);
    }
}
