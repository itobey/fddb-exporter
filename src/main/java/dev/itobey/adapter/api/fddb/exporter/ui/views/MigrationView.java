package dev.itobey.adapter.api.fddb.exporter.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import dev.itobey.adapter.api.fddb.exporter.ui.MainLayout;
import dev.itobey.adapter.api.fddb.exporter.ui.service.ApiException;
import dev.itobey.adapter.api.fddb.exporter.ui.service.MigrationClient;

/**
 * View for data migration operations.
 */
@Route(value = "migration", layout = MainLayout.class)
@PageTitle("Migration | FDDB Exporter")
public class MigrationView extends VerticalLayout {

    private final MigrationClient migrationClient;

    private Div resultDiv;
    private ProgressBar progressBar;

    public MigrationView(MigrationClient migrationClient) {
        this.migrationClient = migrationClient;

        addClassName("migration-view");
        setSpacing(true);
        setPadding(true);
        // Responsive padding - minimum on mobile for spacing from edges
        getStyle().set("padding", "clamp(0.5rem, 2vw, 1.5rem)");

        add(new H2("Data Migration"));
        add(new Paragraph("Migrate data between storage backends."));

        add(createMigrationSection());
    }

    private VerticalLayout createMigrationSection() {
        VerticalLayout section = new VerticalLayout();
        section.addClassNames(
                LumoUtility.Padding.LARGE,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.CONTRAST_5
        );
        section.setSpacing(true);
        section.setAlignItems(Alignment.START);
        section.setWidthFull();
        // Responsive padding on mobile
        section.getStyle()
                .set("padding", "clamp(0.5rem, 2vw, 1.5rem)")
                .set("box-sizing", "border-box");

        // MongoDB to InfluxDB Migration
        Div migrationCard = new Div();
        migrationCard.addClassNames(
                LumoUtility.Padding.LARGE,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Background.BASE,
                LumoUtility.BoxShadow.SMALL
        );
        migrationCard.setWidthFull();
        migrationCard.getStyle()
                .set("padding", "clamp(0.75rem, 2vw, 1.5rem)")
                .set("box-sizing", "border-box");

        VerticalLayout cardContent = new VerticalLayout();
        cardContent.setPadding(false);
        cardContent.setSpacing(true);

        // Header with icon
        Div header = new Div();
        header.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.MEDIUM);
        header.getStyle().set("flex-wrap", "wrap");
        Icon dbIcon = new Icon(VaadinIcon.DATABASE);
        dbIcon.setSize("32px");
        dbIcon.addClassNames(LumoUtility.TextColor.PRIMARY);
        Span title = new Span("MongoDB → InfluxDB Migration");
        title.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.SEMIBOLD);
        header.add(dbIcon, title);

        Paragraph description = new Paragraph(
                "Migrate all stored FDDB data from MongoDB to InfluxDB. " +
                        "This is useful for time-series analysis and visualization in tools like Grafana."
        );
        description.addClassNames(LumoUtility.TextColor.SECONDARY);
        description.getStyle()
                .set("word-wrap", "break-word")
                .set("overflow-wrap", "break-word");

        // Warning notice
        Div warningNotice = new Div();
        warningNotice.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.SMALL,
                LumoUtility.Background.WARNING_10
        );
        Span warningText = new Span("⚠️ Both MongoDB and InfluxDB must be available and properly configured.");
        warningText.addClassNames(LumoUtility.FontSize.SMALL);
        warningNotice.add(warningText);

        Button migrateButton = new Button("Start Migration");
        migrateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        migrateButton.setIcon(new Icon(VaadinIcon.PLAY));
        migrateButton.addClickListener(e -> confirmMigration());
        migrateButton.setWidthFull();

        progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setWidthFull();

        resultDiv = new Div();
        resultDiv.setVisible(false);
        resultDiv.setWidthFull();

        cardContent.add(header, description, warningNotice, migrateButton, progressBar, resultDiv);
        migrationCard.add(cardContent);
        section.add(migrationCard);

        return section;
    }

    private void confirmMigration() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Confirm Migration");
        dialog.setText("Are you sure you want to migrate all MongoDB entries to InfluxDB? " +
                "This may take some time depending on the amount of data.");

        dialog.setCancelable(true);
        dialog.setConfirmText("Start Migration");
        dialog.setConfirmButtonTheme("primary");

        dialog.addConfirmListener(e -> runMigration());

        dialog.open();
    }

    private void runMigration() {
        progressBar.setVisible(true);
        resultDiv.setVisible(false);

        try {
            String result = migrationClient.migrateToInfluxDb();
            displayResult(result, true);
            showSuccess("Migration completed successfully");
        } catch (ApiException e) {
            displayResult(e.getMessage(), false);
            showError("Migration failed: " + e.getMessage());
        } finally {
            progressBar.setVisible(false);
        }
    }

    private void displayResult(String message, boolean success) {
        resultDiv.removeAll();
        resultDiv.setVisible(true);

        Div resultCard = new Div();
        resultCard.addClassNames(
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BorderRadius.SMALL,
                LumoUtility.Margin.Top.MEDIUM
        );

        if (success) {
            resultCard.addClassNames(LumoUtility.Background.SUCCESS_10);
            Icon successIcon = new Icon(VaadinIcon.CHECK_CIRCLE);
            successIcon.addClassNames(LumoUtility.TextColor.SUCCESS);
            Span resultText = new Span(message);
            resultText.addClassNames(LumoUtility.TextColor.SUCCESS);

            Div content = new Div();
            content.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.SMALL);
            content.add(successIcon, resultText);
            resultCard.add(content);
        } else {
            resultCard.addClassNames(LumoUtility.Background.ERROR_10);
            Icon errorIcon = new Icon(VaadinIcon.EXCLAMATION_CIRCLE);
            errorIcon.addClassNames(LumoUtility.TextColor.ERROR);
            Span resultText = new Span(message);
            resultText.addClassNames(LumoUtility.TextColor.ERROR);

            Div content = new Div();
            content.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.SMALL);
            content.add(errorIcon, resultText);
            resultCard.add(content);
        }

        resultDiv.add(resultCard);
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        notification.setDuration(3000);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setDuration(5000);
    }
}

