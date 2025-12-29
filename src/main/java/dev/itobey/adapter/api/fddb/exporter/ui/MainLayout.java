package dev.itobey.adapter.api.fddb.exporter.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.service.VersionCheckService;
import dev.itobey.adapter.api.fddb.exporter.ui.views.*;
import org.springframework.boot.info.BuildProperties;

import java.util.Optional;

/**
 * Main layout providing navigation structure for the FDDB Exporter UI.
 */
@CssImport("./styles/styles.css")
public class MainLayout extends AppLayout {

    private final String appVersion;
    private final VersionCheckService versionCheckService;

    // Use Optional<BuildProperties> so the dependency is optional (works if build-info was not generated)
    public MainLayout(Optional<BuildProperties> buildProperties, VersionCheckService versionCheckService) {
        this.appVersion = buildProperties.map(BuildProperties::getVersion)
                .map(v -> "v" + v)
                .orElse("vdev");
        this.versionCheckService = versionCheckService;

        createHeader();
        createDrawer();
    }

    private void createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        // Keep small margin for visual spacing
        toggle.getStyle().set("margin-right", "0.25rem");

        H1 logo = new H1("FDDB Exporter");
        logo.addClassNames(
                LumoUtility.FontSize.LARGE,
                LumoUtility.Margin.NONE
        );
        // Add a class so CSS in index.html can prevent wrapping/overflow on mobile portrait
        logo.addClassName("app-title");
        // Make logo text responsive and prevent wrapping
        logo.getStyle()
                .set("font-size", "clamp(1rem, 5vw, 1.5rem)")
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis");

        // Left container for toggle
        HorizontalLayout left = new HorizontalLayout(toggle);
        left.setPadding(false);
        left.setSpacing(false);
        left.setWidth("2.5rem");
        left.setAlignItems(FlexComponent.Alignment.CENTER);

        // Center container for logo
        HorizontalLayout center = new HorizontalLayout(logo);
        center.setPadding(false);
        center.setSpacing(false);
        center.setAlignItems(FlexComponent.Alignment.CENTER);
        // Ensure the center block itself is centered within its grid cell
        center.getStyle().set("justify-self", "center");
        center.getStyle().set("width", "100%");

        // Right spacer to balance the left container so the logo remains visually centered
        Span rightSpacer = new Span();
        rightSpacer.setWidth("2.5rem");

        var header = new HorizontalLayout(left, center, rightSpacer);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.setSpacing(false);
        header.addClassNames(
                LumoUtility.Padding.Vertical.SMALL,
                LumoUtility.Padding.Horizontal.SMALL
        );
        // Add a stable class so frontend CSS (.app-header) can target this container
        header.addClassName("app-header");
        // Use CSS Grid to guarantee three columns (fixed-left, center, fixed-right).
        header.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "2.5rem 1fr 2.5rem")
                .set("align-items", "center")
                .set("padding", "0.5rem clamp(0.5rem, 2vw, 1rem)")
                .set("height", "var(--lumo-size-xl)");

        // Remove any previous absolute positioning on toggle; let grid place it in the left cell
        toggle.getElement().getStyle().remove("position");
        toggle.getElement().getStyle().remove("left");
        toggle.getElement().getStyle().remove("top");
        toggle.getElement().getStyle().remove("transform");
        toggle.getElement().getStyle().remove("z-index");

        addToNavbar(header);
    }

    private void createDrawer() {
        SideNav nav = new SideNav();
        nav.addClassNames(LumoUtility.Padding.SMALL);

        nav.addItem(new SideNavItem("Dashboard",
                DashboardView.class,
                new Icon(VaadinIcon.DASHBOARD)));

        nav.addItem(new SideNavItem("Data Export",
                DataExportView.class,
                new Icon(VaadinIcon.DOWNLOAD)));

        nav.addItem(new SideNavItem("Data Query",
                DataQueryView.class,
                new Icon(VaadinIcon.SEARCH)));

        nav.addItem(new SideNavItem("Correlation Analysis",
                CorrelationView.class,
                new Icon(VaadinIcon.CHART)));

        nav.addItem(new SideNavItem("Rolling Averages",
                RollingAveragesView.class,
                new Icon(VaadinIcon.TRENDING_UP)));

        nav.addItem(new SideNavItem("Migration",
                MigrationView.class,
                new Icon(VaadinIcon.DATABASE)));

        // Create version info container
        VerticalLayout versionContainer = new VerticalLayout();
        versionContainer.setPadding(false);
        versionContainer.setSpacing(false);
        // Minimal padding to prevent text wrapping on mobile
        versionContainer.getStyle()
                .set("padding", "0.75rem 0.25rem 0.75rem 0.5rem");

        Span appVersionSpan = new Span(this.appVersion);
        appVersionSpan.addClassNames(
                LumoUtility.FontSize.SMALL,
                LumoUtility.TextColor.SECONDARY
        );
        versionContainer.add(appVersionSpan);

        // Check if there's a new version available
        Optional<String> latestVersion = versionCheckService.getLatestVersionIfNewer();
        if (latestVersion.isPresent()) {
            Optional<String> releaseUrl = versionCheckService.getReleaseUrl();
            Icon updateIcon = VaadinIcon.ARROW_CIRCLE_UP.create();
            updateIcon.setSize("14px");
            updateIcon.getStyle().set("color", "var(--lumo-success-color)");

            String updateText = updateIcon.getElement().toString() + " New Version " + latestVersion.get() + " available";

            if (releaseUrl.isPresent()) {
                Anchor updateLink = new Anchor(releaseUrl.get());
                updateLink.setTarget("_blank");
                updateLink.addClassNames(
                        LumoUtility.FontSize.SMALL,
                        LumoUtility.FontWeight.SEMIBOLD
                );
                updateLink.getStyle()
                        .set("color", "var(--lumo-success-color)")
                        .set("text-decoration", "none")
                        .set("display", "flex")
                        .set("align-items", "center")
                        .set("gap", "0.25rem")
                        .set("white-space", "nowrap");

                updateLink.add(updateIcon);
                updateLink.add(new Span("New Version " + latestVersion.get() + " available"));

                versionContainer.add(updateLink);
            } else {
                HorizontalLayout updateLayout = new HorizontalLayout(updateIcon, new Span("New Version " + latestVersion.get() + " available"));
                updateLayout.setSpacing(false);
                updateLayout.setPadding(false);
                updateLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                updateLayout.addClassNames(
                        LumoUtility.FontSize.SMALL,
                        LumoUtility.FontWeight.SEMIBOLD
                );
                updateLayout.getStyle()
                        .set("color", "var(--lumo-success-color)")
                        .set("gap", "0.25rem")
                        .set("white-space", "nowrap");
                versionContainer.add(updateLayout);
            }
        }

        addToDrawer(nav, versionContainer);
    }
}
