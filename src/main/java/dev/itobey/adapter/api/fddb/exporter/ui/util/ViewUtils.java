package dev.itobey.adapter.api.fddb.exporter.ui.util;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;

public class ViewUtils {

    private ViewUtils() {
    }

    public static VerticalLayout createSection(String backgroundColor) {
        VerticalLayout section = new VerticalLayout();
        section.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.CONTRAST_5
        );
        section.setSpacing(true);
        if (backgroundColor != null) {
            section.getStyle().set("background-color", backgroundColor);
        }
        return section;
    }

    public static Div createCard(String... additionalClasses) {
        Div card = new Div();
        card.addClassName("card");
        card.addClassNames(
                LumoUtility.Padding.LARGE,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.CONTRAST_5
        );
        card.getStyle()
                .set("min-width", "120px")
                .set("max-width", "100%")
                .set("box-sizing", "border-box");

        if (additionalClasses != null) {
            card.addClassNames(additionalClasses);
        }
        return card;
    }

    public static Component createStatCard(String title, String value, String subtitle) {
        Div card = createCard(
                LumoUtility.Display.FLEX,
                LumoUtility.FlexDirection.COLUMN,
                LumoUtility.AlignItems.CENTER
        );

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Span valueSpan = new Span(value);
        valueSpan.addClassNames(LumoUtility.FontSize.XXLARGE, LumoUtility.FontWeight.BOLD);

        Span subtitleSpan = new Span(subtitle);
        subtitleSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);

        card.add(titleSpan, valueSpan, subtitleSpan);
        return card;
    }

    public static Component createNutrientCard(String nutrient, String value, String unit, String emoji, String backgroundColor) {
        Div card = createCard();
        card.addClassNames(LumoUtility.Padding.MEDIUM);
        card.getStyle()
                .set("min-width", "100px");
        // Only set background-color when a color is provided; otherwise keep the default .card styling
        if (backgroundColor != null) {
            card.getStyle().set("background-color", backgroundColor);
        }

        Span emojiSpan = new Span(emoji);
        emojiSpan.addClassNames(LumoUtility.FontSize.XXLARGE);

        Span nutrientSpan = new Span(nutrient);
        nutrientSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD);

        Span valueSpan = new Span(value + " " + unit);
        valueSpan.addClassNames(LumoUtility.FontSize.LARGE);

        VerticalLayout layout = new VerticalLayout(emojiSpan, nutrientSpan, valueSpan);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        card.add(layout);
        return card;
    }

    public static DatePicker.DatePickerI18n createDatePickerI18n() {
        DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
        i18n.setDateFormat("yyyy-MM-dd");
        i18n.setFirstDayOfWeek(1);
        i18n.setToday("Today");
        i18n.setCancel("Cancel");
        return i18n;
    }

    public static String formatNumber(double value) {
        return String.format("%.1f", value);
    }

    public static Div createCardsGrid(String minCardWidth) {
        Div grid = new Div();
        grid.setWidthFull();
        grid.addClassNames(LumoUtility.Gap.MEDIUM);
        grid.addClassName("cards-grid");
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(" + minCardWidth + ", 1fr))")
                .set("gap", "0.75rem");
        return grid;
    }

    public static void applyResponsivePadding(Component component) {
        component.getElement().getStyle().set("padding", "clamp(0.5rem, 2vw, 1.5rem)");
    }

    /**
     * Show a success notification with custom green accent color
     */
    public static void showSuccess(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        notification.setDuration(3000);
        // Apply custom color styling
        notification.getElement().getThemeList().add("success");
    }

    /**
     * Show an error notification with custom red accent color
     */
    public static void showError(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setDuration(5000);
        // Apply custom color styling
        notification.getElement().getThemeList().add("error");
    }

    /**
     * Check if MongoDB is enabled in the application configuration
     */
    public static boolean isMongoDbEnabled(FddbExporterProperties properties) {
        return properties.getPersistence() != null
                && properties.getPersistence().getMongodb() != null
                && properties.getPersistence().getMongodb().isEnabled();
    }

    /**
     * Create a MongoDB disabled error message component
     *
     * @param featureName the name of the feature that requires MongoDB (e.g., "Correlation Analysis")
     */
    public static VerticalLayout createMongoDbDisabledWarning(String featureName) {
        VerticalLayout errorContainer = new VerticalLayout();
        errorContainer.addClassNames(LumoUtility.Padding.LARGE, LumoUtility.BorderRadius.MEDIUM);
        errorContainer.setSpacing(true);
        errorContainer.getStyle()
                .set("background", "rgba(154, 75, 85, 0.1)")
                .set("border", "2px solid rgba(154, 75, 85, 0.3)")
                .set("max-width", "600px")
                .set("margin", "0 auto");

        Icon errorIcon = new Icon(VaadinIcon.EXCLAMATION_CIRCLE_O);
        errorIcon.setSize("48px");
        errorIcon.getStyle().set("color", "#9a4b55");

        H3 errorTitle = new H3("MongoDB Not Enabled");
        errorTitle.getStyle().set("color", "#9a4b55").set("margin", "0.5rem 0");

        Paragraph errorMessage = new Paragraph(
                "The " + featureName + " feature requires MongoDB to be enabled. " +
                        "Please enable MongoDB persistence in your application configuration."
        );
        errorMessage.addClassName(LumoUtility.TextColor.SECONDARY);

        Paragraph configHint = new Paragraph(
                "Set the environment variable FDDB-EXPORTER_PERSISTENCE_MONGODB_ENABLED to true " +
                        "or update the application.yml configuration."
        );
        configHint.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.TERTIARY);
        configHint.getStyle()
                .set("background", "rgba(0, 0, 0, 0.05)")
                .set("padding", "0.75rem")
                .set("border-radius", "4px")
                .set("font-family", "monospace");

        errorContainer.add(errorIcon, errorTitle, errorMessage, configHint);
        errorContainer.setAlignItems(FlexComponent.Alignment.CENTER);

        return errorContainer;
    }
}
