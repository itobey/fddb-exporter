package dev.itobey.adapter.api.fddb.exporter.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.*;
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

public class MainLayout extends AppLayout {

    private static final String ICON_SIZE = "32px";
    private static final String TOGGLE_WIDTH = "2.5rem";
    private static final String ICON_PATH = "/icons/icon.png";

    private final String appVersion;
    private final VersionCheckService versionCheckService;

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
        toggle.getStyle().set("margin-right", "0.25rem");

        H1 logo = new H1("FDDB Exporter");
        logo.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);
        logo.addClassName("app-title");

        Image headerIcon = new Image(ICON_PATH, "FDDB Exporter");
        headerIcon.setHeight(ICON_SIZE);
        headerIcon.setWidth(ICON_SIZE);
        headerIcon.getStyle().set("object-fit", "contain");
        headerIcon.addClassName("fddb-app-header-icon");

        HorizontalLayout left = new HorizontalLayout(toggle);
        left.setPadding(false);
        left.setSpacing(false);
        left.setWidth(TOGGLE_WIDTH);
        left.setAlignItems(FlexComponent.Alignment.CENTER);

        HorizontalLayout center = new HorizontalLayout(headerIcon, logo);
        center.setPadding(false);
        center.setSpacing(false);
        center.setAlignItems(FlexComponent.Alignment.CENTER);
        center.addClassName("app-header-center");
        center.getStyle().set("justify-self", "center").set("width", "100%");

        Span rightSpacer = new Span();
        rightSpacer.setWidth(TOGGLE_WIDTH);

        HorizontalLayout header = new HorizontalLayout(left, center, rightSpacer);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.setSpacing(false);
        header.addClassNames(LumoUtility.Padding.Vertical.SMALL, LumoUtility.Padding.Horizontal.SMALL);
        header.addClassName("app-header");
        header.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", TOGGLE_WIDTH + " 1fr " + TOGGLE_WIDTH)
                .set("align-items", "center")
                .set("padding", "0.5rem clamp(0.5rem, 2vw, 1rem)")
                .set("height", "var(--lumo-size-xl)");

        addToNavbar(header);
    }

    private void createDrawer() {
        H3 drawerTitle = new H3("FDDB-Exporter");
        drawerTitle.addClassNames(LumoUtility.Margin.NONE);
        drawerTitle.getStyle()
                .set("color", "var(--lumo-header-text-color)")
                .set("font-size", "1.25rem")
                .set("font-weight", "600");

        Image appIcon = new Image(ICON_PATH, "FDDB Exporter");
        appIcon.setHeight(ICON_SIZE);
        appIcon.setWidth(ICON_SIZE);
        appIcon.getStyle().set("object-fit", "contain").set("margin-right", "0.5rem");
        appIcon.addClassName("fddb-drawer-icon");

        HorizontalLayout drawerHeader = new HorizontalLayout(appIcon, drawerTitle);
        drawerHeader.setAlignItems(FlexComponent.Alignment.CENTER);
        drawerHeader.addClassName("drawer-header");

        SideNav nav = new SideNav();
        nav.addClassNames(LumoUtility.Padding.SMALL);

        nav.addItem(new SideNavItem("Dashboard", DashboardView.class, new Icon(VaadinIcon.DASHBOARD)));
        nav.addItem(new SideNavItem("Data Export", DataExportView.class, new Icon(VaadinIcon.DOWNLOAD)));
        nav.addItem(new SideNavItem("Data Query", DataQueryView.class, new Icon(VaadinIcon.SEARCH)));
        nav.addItem(new SideNavItem("Rolling Averages", RollingAveragesView.class, new Icon(VaadinIcon.TRENDING_UP)));
        nav.addItem(new SideNavItem("Correlation Analysis", CorrelationView.class, new Icon(VaadinIcon.CHART)));
        nav.addItem(new SideNavItem("Settings", SettingsView.class, new Icon(VaadinIcon.COGS)));

        addToDrawer(drawerHeader, nav, createVersionInfo());
    }

    private VerticalLayout createVersionInfo() {
        VerticalLayout versionContainer = new VerticalLayout();
        versionContainer.setPadding(false);
        versionContainer.setSpacing(false);
        versionContainer.getStyle().set("padding", "0.75rem 0.25rem 0.75rem 0.5rem");

        Span appVersionSpan = new Span(this.appVersion);
        appVersionSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        versionContainer.add(appVersionSpan);

        Optional<String> latestVersion = versionCheckService.getLatestVersionIfNewer();
        if (latestVersion.isPresent()) {
            Icon updateIcon = VaadinIcon.ARROW_CIRCLE_UP.create();
            updateIcon.setSize("14px");
            updateIcon.getStyle().set("color", "#3f908c");

            Optional<String> releaseUrl = versionCheckService.getReleaseUrl();
            if (releaseUrl.isPresent()) {
                Anchor updateLink = new Anchor(releaseUrl.get());
                updateLink.setTarget("_blank");
                updateLink.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD);
                updateLink.getStyle()
                        .set("color", "#3f908c")
                        .set("text-decoration", "none")
                        .set("display", "flex")
                        .set("align-items", "center")
                        .set("gap", "0.25rem")
                        .set("white-space", "nowrap");
                updateLink.add(updateIcon, new Span("New Version " + latestVersion.get() + " available"));
                versionContainer.add(updateLink);
            } else {
                HorizontalLayout updateLayout = new HorizontalLayout(updateIcon,
                        new Span("New Version " + latestVersion.get() + " available"));
                updateLayout.setSpacing(false);
                updateLayout.setPadding(false);
                updateLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                updateLayout.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD);
                updateLayout.getStyle()
                        .set("color", "#3f908c")
                        .set("gap", "0.25rem")
                        .set("white-space", "nowrap");
                versionContainer.add(updateLayout);
            }
        }

        return versionContainer;
    }
}


