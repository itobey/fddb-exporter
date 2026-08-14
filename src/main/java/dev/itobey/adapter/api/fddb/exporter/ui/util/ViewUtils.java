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
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.function.SerializableRunnable;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;

public class ViewUtils {

    private ViewUtils() {
    }

    /**
     * Bring a freshly rendered result block to the top of the viewport, once it has stopped growing.
     * <p>
     * Calling {@code scrollIntoView} straight after the server pushes the result lands short of the
     * block: a Vaadin Grid sizes itself a frame or more after its rows arrive, and a smooth scroll
     * fixes its destination the moment it starts, so it aims into a document that is still hundreds
     * of pixels shorter than the final one and never corrects afterwards. On Rolling Averages that
     * left the page parked on the preset buttons instead of on the averages. Waiting for the
     * block's own height to hold still for a few frames removes the race; the frame budget is the
     * escape hatch for a block that never settles, so the scroll still happens.
     */
    public static void scrollIntoViewWhenSettled(Component target) {
        target.getElement().executeJs("""
                const block = this;
                // prefers-reduced-motion cannot be applied to a script-initiated scroll from CSS,
                // so the theme's Reduced Motion Rule has to be honoured here explicitly.
                const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches
                    ? 'auto' : 'smooth';
                let lastHeight = -1;
                let stableFrames = 0;
                let framesLeft = 60;
                const settle = () => {
                    const height = block.scrollHeight;
                    if (height === lastHeight) {
                        stableFrames++;
                    } else {
                        stableFrames = 0;
                        lastHeight = height;
                    }
                    if (stableFrames < 3 && framesLeft-- > 0) {
                        requestAnimationFrame(settle);
                        return;
                    }
                    block.scrollIntoView({behavior: behavior, block: 'start'});
                };
                requestAnimationFrame(settle);
                """);
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
        Span valueSpan = new Span(value);
        valueSpan.addClassName("card__value");
        return createStatCard(title, valueSpan, subtitle);
    }

    /**
     * Stat card variant that takes a pre-built value component, for values that need internal
     * structure (e.g. a date range whose two dates must stay unbroken when the card is narrow).
     * The component is expected to carry the {@code card__value} class itself.
     */
    public static Component createStatCard(String title, Component value, String subtitle) {
        Div card = createCard(
                LumoUtility.Display.FLEX,
                LumoUtility.FlexDirection.COLUMN,
                LumoUtility.AlignItems.CENTER
        );

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Span subtitleSpan = new Span(subtitle);
        subtitleSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);

        card.add(titleSpan, value, subtitleSpan);
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

        // Decorative. Without this a screen reader reads the emoji's own name before the label -
        // "butter, Fat, 62.4 g" - on every card of the dashboard.
        Span emojiSpan = new Span(emoji);
        emojiSpan.addClassNames(LumoUtility.FontSize.XXLARGE);
        emojiSpan.getElement().setAttribute("aria-hidden", "true");

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
        // min(<width>, 100%) keeps the track from demanding more than the container on narrow
        // viewports - without it a 320px screen gets a column wider than the screen itself.
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(min(" + minCardWidth + ", 100%), 1fr))")
                .set("gap", "0.75rem");
        return grid;
    }

    public static void applyResponsivePadding(Component component) {
        component.getElement().getStyle().set("padding", "clamp(0.5rem, 2vw, 1.5rem)");
    }

    /**
     * Give a plain container (Div, HorizontalLayout, ...) that is only wired up with a click listener
     * the semantics and keyboard behaviour of a button: assistive technology announces it as a
     * control, Tab reaches it, and Enter/Space activate it just like a native button would.
     * The caller keeps its own click listener and passes the very same action here, so mouse and
     * keyboard run identical code.
     *
     * @param accessibleName announced label, or {@code null} when the component's own visible text
     *                       already describes it completely (a visible label beats a redundant
     *                       aria-label, which would silence the text for screen reader users)
     */
    public static void makeAccessibleButton(Component component, String accessibleName, SerializableRunnable action) {
        Element element = component.getElement();
        element.setAttribute("role", "button");
        element.setAttribute("tabindex", "0");
        // These containers are data first and activatable second - a peak figure with its date, a
        // weekday with its count. The theme switches text selection off for [role="button"] and
        // for anything tabbable, which is right for real controls but would make this content
        // uncopyable; the class opts it back in. See styles.css.
        element.getClassList().add("activatable-data");
        if (accessibleName != null) {
            element.setAttribute("aria-label", accessibleName);
        }
        // "Spacebar" is the legacy key name older browsers still report. preventDefault stops Space
        // from scrolling the page, which is what makes a focused role=button feel native.
        element.addEventListener("keydown", event -> action.run())
                .setFilter("event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar'")
                .preventDefault();
    }

    /**
     * Show a success notification with custom green accent color
     */
    public static void showSuccess(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        notification.setDuration(1500);
        // Apply custom color styling
        notification.getElement().getThemeList().add("success");
    }

    /**
     * Show an error notification with custom red accent color
     */
    public static void showError(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setDuration(1500);
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
                .set("background", "var(--red-accent-surface)")
                .set("border", "2px solid var(--red-accent-surface-border)")
                .set("max-width", "600px")
                .set("margin", "0 auto");

        Icon errorIcon = new Icon(VaadinIcon.EXCLAMATION_CIRCLE_O);
        errorIcon.setSize("48px");
        // The theme's text variant, not the raw fill: --red-accent measures only 1.31:1 as text
        // here, on the one screen a blocked user has to read.
        errorIcon.getStyle().set("color", "var(--red-accent-text)");

        H3 errorTitle = new H3("MongoDB Not Enabled");
        errorTitle.getStyle().set("color", "var(--red-accent-text)").set("margin", "0.5rem 0");

        Paragraph errorMessage = new Paragraph(
                "The " + featureName + " feature requires MongoDB to be enabled. " +
                        "Please enable MongoDB persistence in your application configuration."
        );
        errorMessage.addClassName(LumoUtility.TextColor.SECONDARY);

        Paragraph configHint = new Paragraph(
                "Set the environment variable FDDB-EXPORTER_PERSISTENCE_MONGODB_ENABLED to true " +
                        "or update the application.yml configuration."
        );
        // Secondary, not tertiary: this hint is the recovery instruction, and the tertiary step is
        // deliberately below AA for decorative text only.
        configHint.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        configHint.getStyle()
                .set("background", "var(--code-surface)")
                .set("padding", "0.75rem")
                .set("border-radius", "4px")
                .set("font-family", "monospace");

        errorContainer.add(errorIcon, errorTitle, errorMessage, configHint);
        errorContainer.setAlignItems(FlexComponent.Alignment.CENTER);

        return errorContainer;
    }
}
