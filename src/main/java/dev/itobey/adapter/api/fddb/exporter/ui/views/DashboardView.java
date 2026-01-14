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
import dev.itobey.adapter.api.fddb.exporter.ui.service.StatsClient;
import dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils;

import java.util.Map;

import static dev.itobey.adapter.api.fddb.exporter.ui.util.ViewUtils.*;

@Route(value = "dashboard", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Dashboard | FDDB Exporter")
public class DashboardView extends VerticalLayout {

    private static final String BG_COLOR = "rgba(78, 97, 155, 0.08)";
    private final StatsClient statsClient;

    public DashboardView(StatsClient statsClient) {
        this.statsClient = statsClient;

        addClassName("dashboard-view");
        setSpacing(true);
        setPadding(true);
        applyResponsivePadding(this);

        add(new H2("Dashboard"));
        loadStats();
    }

    private void loadStats() {
        try {
            displayStats(statsClient.getStats());
        } catch (ApiException e) {
            showError("Failed to load statistics: " + e.getMessage());
        }
    }

    private void displayStats(StatsDTO stats) {
        Div overviewCards = createCardsGrid("140px");
        overviewCards.add(
                createStatCard("Total Entries", String.valueOf(stats.getAmountEntries()), "entries in database"),
                createStatCard("Entry Coverage", String.format("%.1f%%", stats.getEntryPercentage()), "of days tracked"),
                createStatCard("Unique Products", String.valueOf(stats.getUniqueProducts()), "different products"),
                createStatCard("First Entry", stats.getFirstEntryDate() != null ? stats.getFirstEntryDate().toString() : "N/A", "start date"),
                createStatCard("Missing Day", stats.getMostRecentMissingDay() != null ? stats.getMostRecentMissingDay().toString() : "N/A", "most recent")
        );
        add(overviewCards);

        if (stats.getAverageTotals() != null) {
            add(new H3("Average Daily Nutrition"));
            Div averageCards = createCardsGrid("120px");
            StatsDTO.Averages avg = stats.getAverageTotals();
            averageCards.add(
                    createNutrientCard("Calories", formatNumber(avg.getAvgTotalCalories()), "kcal", "🔥", BG_COLOR),
                    createNutrientCard("Fat", formatNumber(avg.getAvgTotalFat()), "g", "🧈", BG_COLOR),
                    createNutrientCard("Carbs", formatNumber(avg.getAvgTotalCarbs()), "g", "🍞", BG_COLOR),
                    createNutrientCard("Sugar", formatNumber(avg.getAvgTotalSugar()), "g", "🍬", BG_COLOR),
                    createNutrientCard("Protein", formatNumber(avg.getAvgTotalProtein()), "g", "🥩", BG_COLOR),
                    createNutrientCard("Fibre", formatNumber(avg.getAvgTotalFibre()), "g", "🥦", BG_COLOR)
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
                .set("background-color", BG_COLOR)
                .set("border-left", "4px solid var(--accent)");

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
        valueSpan.getStyle().set("text-align", "center").set("width", "100%");

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

