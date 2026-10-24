package dev.itobey.adapter.api.fddb.exporter.ui.components;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.select.Select;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ChangelogClient;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ChangelogClient.Release;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ChangelogClient.Snapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** One reading surface for the entire history, with a persistent version jump control. */
public class ChangelogDialog extends Dialog {
    private final ChangelogClient client;
    private final String installedVersion;
    private final Select<Release> versions = new Select<>();
    private final Div notes = new Div();
    private final Paragraph status = new Paragraph();
    private final Button retry = new Button("Retry", event -> load());
    private final Map<Release, H2> headings = new LinkedHashMap<>();
    private CompletableFuture<Snapshot> pending;
    private int generation;

    public ChangelogDialog(ChangelogClient client, String installedVersion) {
        this.client = client;
        this.installedVersion = installedVersion;
        addClassName("changelog-dialog");
        setHeaderTitle("Changelog");
        setCloseOnOutsideClick(false);
        Button close = new Button("Close", event -> close());
        close.addClassName("changelog-close");
        getHeader().add(close);

        Span installed = new Span("Installed version: " + installedVersion);
        installed.addClassName("changelog-installed");
        versions.setLabel("Jump to version");
        versions.setPlaceholder("Choose a version");
        versions.setItemLabelGenerator(Release::version);
        versions.setWidthFull();
        versions.addValueChangeListener(event -> {
            H2 heading = headings.get(event.getValue());
            if (event.isFromClient() && heading != null) {
                heading.getElement().executeJs("this.scrollIntoView({block: 'start', behavior: 'instant'}); this.focus({preventScroll: true})");
                versions.clear();
            }
        });
        Div toolbar = new Div(installed, versions);
        toolbar.addClassName("changelog-toolbar");
        status.getElement().setAttribute("role", "status");
        status.addClassName("changelog-status");
        retry.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Div feedback = new Div(status, retry);
        feedback.addClassName("changelog-feedback");
        notes.addClassName("changelog-notes");
        notes.getElement().setAttribute("role", "region");
        notes.getElement().setAttribute("aria-label", "Release history");
        notes.getElement().setAttribute("tabindex", "0");
        Div body = new Div(toolbar, feedback, notes);
        body.addClassName("changelog-body");
        add(body);

        Anchor source = new Anchor(ChangelogClient.SOURCE_URL, "View CHANGELOG.md on GitHub");
        source.setTarget("_blank");
        source.getElement().setAttribute("rel", "noopener noreferrer");
        getFooter().add(source);
        addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                load();
            } else {
                generation++;
            }
        });
        addDetachListener(event -> generation++);
    }

    private void load() {
        generation++;
        notes.removeAll();
        headings.clear();
        versions.clear();
        versions.setEnabled(false);
        retry.setVisible(false);
        status.setText("Loading release history from GitHub…");
        notes.getElement().setAttribute("aria-busy", "true");
        pending = client.load();
        checkLoad(generation);
    }

    /** A short-lived client callback delivers async results without enabling global UI polling/push. */
    @ClientCallable
    public void checkLoad(int request) {
        if (request != generation || !isOpened()) {
            return;
        }
        if (!pending.isDone()) {
            getElement().executeJs("setTimeout(() => { if (this.opened && this.isConnected) this.$server.checkLoad($0); }, 300)", request);
            return;
        }
        notes.getElement().setAttribute("aria-busy", "false");
        try {
            Snapshot snapshot = pending.join();
            status.setText(snapshot.stale()
                    ? "GitHub is unavailable. Showing saved release notes from " + snapshot.fetchedAt() + "."
                    : "All documented versions, newest first.");
            versions.setItems(snapshot.releases());
            for (Release release : snapshot.releases()) {
                H2 heading = new H2(release.version());
                heading.getElement().setAttribute("tabindex", "-1");
                Div section = new Div(heading);
                section.addClassName("changelog-release");
                if (release.version().equals(installedVersion.replaceFirst("^v", ""))) {
                    Span badge = new Span("Installed");
                    badge.addClassName("changelog-installed-marker");
                    section.add(badge);
                }
                section.add(new Markdown(release.markdown()));
                headings.put(release, heading);
                notes.add(section);
            }
            versions.setEnabled(true);
            notes.getElement().executeJs("this.scrollTop = 0");
        } catch (RuntimeException failure) {
            status.setText("Couldn’t load the changelog from GitHub. Try again or use the GitHub link below.");
            retry.setVisible(true);
        }
    }
}
