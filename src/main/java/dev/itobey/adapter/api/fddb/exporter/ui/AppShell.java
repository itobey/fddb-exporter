package dev.itobey.adapter.api.fddb.exporter.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * Configuration class for Vaadin application shell.
 * Defines the theme and other app-level settings.
 */
@Theme(value = "fddb-exporter", variant = Lumo.DARK)
@PWA(
        name = "FDDB Exporter",
        shortName = "FDDB-Exporter",
        description = "Export and analyze your FDDB nutrition data",
        themeColor = "#4e619b",
        backgroundColor = "#4e619b",
        offlinePath = "offline.html",
        startPath = "/",
        display = "standalone",
        // Use iconPath to specify custom icon with purpose="any" for transparency (no masking/zoom)
        iconPath = "icons/icon.png",
        // Disable manifest URL to force regeneration with proper settings
        manifestPath = "manifest.webmanifest"
)
public class AppShell implements AppShellConfigurator {
}

