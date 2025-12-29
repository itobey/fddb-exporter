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

import java.util.HashMap;
import java.util.Map;

/**
 * Dashboard view displaying overall statistics.
 */
@Route(value = "dashboard", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Dashboard | FDDB Exporter")
public class DashboardView extends VerticalLayout {

    private final StatsClient statsClient;

    public DashboardView(StatsClient statsClient) {
        this.statsClient = statsClient;

        addClassName("dashboard-view");
        setSpacing(true);
        setPadding(true);
        // Responsive padding
        getStyle().set("padding", "clamp(0.5rem, 2vw, 1.5rem)");

        add(new H2("Dashboard"));

        loadStats();
    }

    private void loadStats() {
        try {
            StatsDTO stats = statsClient.getStats();
            displayStats(stats);
        } catch (ApiException e) {
            showError("Failed to load statistics: " + e.getMessage());
        }
    }

    private void displayStats(StatsDTO stats) {
        // Overview cards - use CSS grid so cards wrap naturally
        Div overviewCards = new Div();
        overviewCards.setWidthFull();
        overviewCards.addClassNames(LumoUtility.Gap.MEDIUM);
        overviewCards.addClassName("cards-grid");
        overviewCards.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(140px, 1fr))")
                .set("gap", "0.75rem");

        overviewCards.add(
                createStatCard("Total Entries", String.valueOf(stats.getAmountEntries()), "entries in database"),
                createStatCard("Entry Coverage", String.format("%.1f%%", stats.getEntryPercentage()), "of days tracked"),
                createStatCard("Unique Products", String.valueOf(stats.getUniqueProducts()), "different products"),
                createStatCard("First Entry", stats.getFirstEntryDate() != null ? stats.getFirstEntryDate().toString() : "N/A", "start date"),
                createStatCard("Missing Day", stats.getMostRecentMissingDay() != null ? stats.getMostRecentMissingDay().toString() : "N/A", "most recent")
        );

        add(overviewCards);


        // Average totals section
        if (stats.getAverageTotals() != null) {
            add(new H3("Average Daily Nutrition"));
            Div averageCards = new Div();
            averageCards.setWidthFull();
            averageCards.addClassNames(LumoUtility.Gap.MEDIUM);
            averageCards.addClassName("cards-grid");
            averageCards.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "repeat(auto-fit, minmax(120px, 1fr))")
                    .set("gap", "0.75rem");

            StatsDTO.Averages avg = stats.getAverageTotals();
            averageCards.add(
                    createNutrientCard("Calories", formatNumber(avg.getAvgTotalCalories()), "kcal", "🔥", "rgba(78, 97, 155, 0.08)"),
                    createNutrientCard("Fat", formatNumber(avg.getAvgTotalFat()), "g", "🧈", "rgba(78, 97, 155, 0.08)"),
                    createNutrientCard("Carbs", formatNumber(avg.getAvgTotalCarbs()), "g", "🍞", "rgba(78, 97, 155, 0.08)"),
                    createNutrientCard("Sugar", formatNumber(avg.getAvgTotalSugar()), "g", "🍬", "rgba(78, 97, 155, 0.08)"),
                    createNutrientCard("Protein", formatNumber(avg.getAvgTotalProtein()), "g", "🥩", "rgba(78, 97, 155, 0.08)"),
                    createNutrientCard("Fibre", formatNumber(avg.getAvgTotalFibre()), "g", "🥦", "rgba(78, 97, 155, 0.08)")
            );
            add(averageCards);
        }

        // Highest values section
        add(new H3("Highest Daily Values"));
        Div highestCards = new Div();
        highestCards.setWidthFull();
        highestCards.addClassNames(LumoUtility.Gap.MEDIUM);
        highestCards.addClassName("cards-grid");
        highestCards.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(160px, 1fr))")
                .set("gap", "0.75rem");

        // Reorder to match average cards order
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

    private Component createStatCard(String title, String value, String subtitle) {
        Div card = new Div();
        card.addClassName("card");
        card.addClassNames(
                LumoUtility.Padding.LARGE,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.CONTRAST_5,
                LumoUtility.Display.FLEX,
                LumoUtility.FlexDirection.COLUMN,
                LumoUtility.AlignItems.CENTER
        );
        // Let the grid control sizing; set reasonable min/max so grid fits correctly
        card.getStyle()
                .set("min-width", "120px")
                .set("max-width", "100%")
                .set("box-sizing", "border-box");

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Span valueSpan = new Span(value);
        valueSpan.addClassNames(LumoUtility.FontSize.XXLARGE, LumoUtility.FontWeight.BOLD);

        Span subtitleSpan = new Span(subtitle);
        subtitleSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);

        card.add(titleSpan, valueSpan, subtitleSpan);
        return card;
    }

    private Component createNutrientCard(String nutrient, String value, String unit, String emoji, String backgroundColor) {
        Div card = new Div();
        card.addClassName("card");
        card.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.MEDIUM
        );
        card.getStyle()
                .set("min-width", "100px")
                .set("max-width", "100%")
                .set("box-sizing", "border-box")
                .set("background-color", backgroundColor);

        Span emojiSpan = new Span(emoji);
        emojiSpan.addClassNames(LumoUtility.FontSize.XXLARGE);

        Span nutrientSpan = new Span(nutrient);
        nutrientSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD);

        Span valueSpan = new Span(value + " " + unit);
        valueSpan.addClassNames(LumoUtility.FontSize.LARGE);

        VerticalLayout layout = new VerticalLayout(emojiSpan, nutrientSpan, valueSpan);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setAlignItems(Alignment.CENTER);
        card.add(layout);
        return card;
    }

    private Component createHighestCard(String nutrient, StatsDTO.DayStats dayStats, String emoji, String unit) {
        Div card = new Div();
        card.addClassName("card");
        card.addClassName("card--highlight");
        card.addClassNames(
                LumoUtility.Padding.LARGE,
                LumoUtility.BorderRadius.LARGE
        );
        card.getStyle()
                .set("min-width", "140px")
                .set("max-width", "100%")
                .set("box-sizing", "border-box")
                .set("background-color", "rgba(78, 97, 155, 0.08)")
                .set("border-left", "4px solid var(--accent)");

        // Add click listener to navigate to DataQueryView with date parameter
        card.addClickListener(event -> {
            if (dayStats.getDate() != null) {
                Map<String, String> params = new HashMap<>();
                params.put("date", dayStats.getDate().toString());
                UI.getCurrent().navigate("query", QueryParameters.simple(params));
            }
        });

        // Emoji header
        Span emojiSpan = new Span(emoji);
        emojiSpan.addClassNames(LumoUtility.FontSize.XXLARGE);
        emojiSpan.getStyle().set("display", "block").set("margin-bottom", "0.5rem");

        // Nutrient label
        Span nutrientSpan = new Span(nutrient);
        nutrientSpan.addClassNames(
                LumoUtility.FontSize.SMALL,
                LumoUtility.FontWeight.SEMIBOLD,
                LumoUtility.TextColor.SECONDARY
        );
        nutrientSpan.addClassName("label-small");

        // Value with unit
        Span valueSpan = new Span(formatNumber(dayStats.getTotal()) + " " + unit);
        valueSpan.addClassNames(LumoUtility.FontSize.XXLARGE, LumoUtility.FontWeight.BOLD);
        valueSpan.addClassName("card__value");
        // Ensure perfect horizontal centering on all devices
        valueSpan.getStyle()
                .set("text-align", "center")
                .set("width", "100%");

        // Date badge
        Div dateBadge = new Div();
        dateBadge.addClassName("date-badge");
        dateBadge.addClassNames(
                LumoUtility.Padding.Horizontal.SMALL,
                LumoUtility.Padding.Vertical.XSMALL,
                LumoUtility.FontSize.XSMALL
        );
        dateBadge.setText(dayStats.getDate() != null ? dayStats.getDate().toString() : "N/A");

        VerticalLayout layout = new VerticalLayout(emojiSpan, nutrientSpan, valueSpan, dateBadge);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        card.add(layout);
        return card;
    }

    private String formatNumber(double value) {
        return String.format("%.1f", value);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setDuration(5000);
    }
}

