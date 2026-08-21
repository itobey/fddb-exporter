package dev.itobey.adapter.api.fddb.exporter.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.SvgIcon;
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
    // The 192px variant, not icon.png: both render into a 32px box, but icon.png is 114 KB
    // against 24 KB here, and 192 still oversamples a 32px slot at 3x device pixel ratio.
    // AppShell keeps pointing @PWA at icon.png, which is the installable icon and a
    // different job from this one.
    private static final String ICON_PATH = "/icons/icon-192x192.png";

    private static final String GITHUB_URL = "https://github.com/itobey/fddb-exporter";
    private static final String DOCS_URL = "https://itobey.github.io/fddb-exporter/";

    private static final String GITHUB_MARK_PATH = "icons/github.svg";

    private final String appVersion;
    private final VersionCheckService versionCheckService;

    public MainLayout(Optional<BuildProperties> buildProperties, VersionCheckService versionCheckService) {
        this.appVersion = buildProperties.map(BuildProperties::getVersion)
                .orElse("dev");
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

        // Decorative: the logo sits directly beside the visible "FDDB Exporter" title, so a
        // matching alt would make a screen reader announce the name twice.
        Image headerIcon = new Image(ICON_PATH, "");
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

        // Decorative, same reason as the header icon: the drawer title carries the name.
        Image appIcon = new Image(ICON_PATH, "");
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
        nav.addItem(new SideNavItem("Entries", EntriesView.class, new Icon(VaadinIcon.SEARCH)));
        nav.addItem(new SideNavItem("Products", ProductsView.class, new Icon(VaadinIcon.CUBES)));
        nav.addItem(new SideNavItem("Rolling Averages", RollingAveragesView.class, new Icon(VaadinIcon.TRENDING_UP)));
        nav.addItem(new SideNavItem("Trends", TrendView.class, new Icon(VaadinIcon.LINE_CHART)));
        nav.addItem(new SideNavItem("Correlation Analysis", CorrelationView.class, new Icon(VaadinIcon.CHART)));
        nav.addItem(new SideNavItem("Data Download", DataDownloadView.class, new Icon(VaadinIcon.CLOUD_DOWNLOAD)));
        nav.addItem(new SideNavItem("Settings", SettingsView.class, new Icon(VaadinIcon.COGS)));

        addToDrawer(drawerHeader, nav, createAboutStrip());
    }

    /**
     * Drawer footer: what this build is, and where it came from.
     */
    private VerticalLayout createAboutStrip() {
        VerticalLayout aboutStrip = new VerticalLayout();
        aboutStrip.setPadding(false);
        aboutStrip.setSpacing(false);
        aboutStrip.addClassName("drawer-about");

        Span appVersionSpan = new Span("Version " + this.appVersion);
        appVersionSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        aboutStrip.add(appVersionSpan);

        createUpdateNotice().ifPresent(aboutStrip::add);
        aboutStrip.add(createAboutLinks());

        return aboutStrip;
    }

    /**
     * The update line only exists when a newer release was found, and only links out when the
     * check also returned a release URL.
     */
    private Optional<Component> createUpdateNotice() {
        Optional<String> latestVersion = versionCheckService.getLatestVersionIfNewer();
        if (latestVersion.isEmpty()) {
            return Optional.empty();
        }

        Icon updateIcon = VaadinIcon.ARROW_CIRCLE_UP.create();
        Span updateText = new Span("New Version " + latestVersion.get() + " available");

        Optional<String> releaseUrl = versionCheckService.getReleaseUrl();
        if (releaseUrl.isPresent()) {
            Anchor updateLink = new Anchor(releaseUrl.get());
            updateLink.setTarget("_blank");
            updateLink.getElement().setAttribute("rel", "noopener noreferrer");
            updateLink.addClassName("drawer-about-update");
            updateLink.add(updateIcon, updateText);
            return Optional.of(updateLink);
        }

        Span updateNotice = new Span(updateIcon, updateText);
        updateNotice.addClassName("drawer-about-update");
        return Optional.of(updateNotice);
    }

    /**
     * Provenance links, kept quiet: these are escape hatches, not part of the daily task flow.
     */
    private HorizontalLayout createAboutLinks() {
        HorizontalLayout aboutLinks = new HorizontalLayout();
        aboutLinks.setPadding(false);
        aboutLinks.setSpacing(false);
        aboutLinks.addClassName("drawer-about-links");

        aboutLinks.add(aboutLink(GITHUB_URL, new SvgIcon(GITHUB_MARK_PATH), "GitHub"));
        aboutLinks.add(aboutLink(DOCS_URL, VaadinIcon.BOOK.create(), "Docs"));

        return aboutLinks;
    }

    private Anchor aboutLink(String href, Component icon, String label) {
        Anchor link = new Anchor(href);
        link.setTarget("_blank");
        link.getElement().setAttribute("rel", "noopener noreferrer");
        link.addClassName("drawer-about-link");
        link.add(icon, new Span(label));
        return link;
    }
}
