package com.tunai.desktop.ui;

import com.tunai.desktop.App;
import com.tunai.desktop.AppContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Acerca de y créditos (§5.6). */
final class AboutDialog {

    private static final Logger LOG = Logger.getLogger(AboutDialog.class.getName());
    private static final String SEPARATOR = " — ";

    /** Una línea de third-party-licenses.txt: "Nombre — Licencia — URL". */
    private record License(String name, String terms, String url) {
    }

    private AboutDialog() {
    }

    static void show(AppContext context) {
        // Texto exacto de atribución exigido por la licencia de Stability AI: PENDIENTE DE VALIDAR (§10).
        Label credit = new Label(Resources.text("/com/tunai/desktop/legal/attribution.txt").strip());
        credit.setWrapText(true);
        credit.getStyleClass().add("muted");
        Node models = section("mdal-3d_rotation", "Modelos 3D", credit);

        List<License> licenses = licenses();
        VBox rows = new VBox();
        rows.getStyleClass().add("license-list");
        licenses.forEach(l -> rows.getChildren().add(licenseRow(l)));
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(210);
        scroll.getStyleClass().addAll("edge-to-edge", "license-scroll");
        Label count = new Label(String.valueOf(licenses.size()));
        count.getStyleClass().add("about-count");
        HBox licensesTitle = new HBox(8, sectionTitle("mdal-gavel", "Licencias de terceros"), count);
        licensesTitle.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(18, hero(), models, new VBox(10, licensesTitle, scroll), installationId(context));
        content.setPadding(new Insets(4, 4, 0, 4));
        content.setPrefWidth(540);
        content.getStyleClass().add("about-dialog");

        Alert alert = new Alert(Alert.AlertType.NONE, null, Dialogs.ACCEPT);
        alert.getDialogPane().setContent(content);
        Dialogs.prepare(alert, context.stage(), "Acerca de TunAI");
        alert.showAndWait();
    }

    private static Node hero() {
        ImageView logo = new ImageView(new Image(AboutDialog.class.getResource("/com/tunai/desktop/icons/icon-128.png").toExternalForm()));
        logo.setFitWidth(72);
        logo.setFitHeight(72);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);

        Label name = new Label(App.NAME);
        name.getStyleClass().add("about-name");
        Label version = new Label("v" + App.version());
        version.getStyleClass().add("about-version");
        Label tagline = new Label("Convierte fotos de tu vehículo en modelos 3D.");
        tagline.getStyleClass().add("muted");
        HBox nameRow = new HBox(10, name, version);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        HBox hero = new HBox(16, logo, new VBox(2, nameRow, tagline));
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("about-hero");
        return hero;
    }

    private static HBox sectionTitle(String icon, String text) {
        FontIcon graphic = new FontIcon(icon);
        graphic.getStyleClass().add("icon-accent");
        Label label = new Label(text, graphic);
        label.getStyleClass().add("about-section-title");
        HBox box = new HBox(label);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Node section(String icon, String title, Node body) {
        VBox box = new VBox(6, sectionTitle(icon, title), body);
        box.getStyleClass().add("about-card");
        return box;
    }

    private static Node licenseRow(License license) {
        Label name = new Label(license.name());
        name.getStyleClass().add("text-bold");
        name.setWrapText(true);
        Label terms = new Label(license.terms());
        terms.getStyleClass().addAll("muted", "small");
        terms.setWrapText(true);
        VBox text = new VBox(2, name, terms);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(10, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("license-row");
        if (license.url() != null) {
            Button open = new Button(null, new FontIcon("mdmz-open_in_new"));
            open.getStyleClass().addAll("button-icon", "flat");
            open.setTooltip(new Tooltip(license.url()));
            open.setOnAction(e -> browse(license.url()));
            row.getChildren().add(open);
        }
        return row;
    }

    private static Node installationId(AppContext context) {
        String id = context.installationId().value().toString();
        Label caption = new Label("ID de instalación", new FontIcon("mdal-fingerprint"));
        caption.getStyleClass().addAll("muted", "small");

        Label value = new Label(id);
        value.getStyleClass().add("about-id");
        HBox.setHgrow(value, Priority.ALWAYS);
        value.setMaxWidth(Double.MAX_VALUE);

        FontIcon copyIcon = new FontIcon("mdal-content_copy");
        Button copy = new Button("Copiar", copyIcon);
        copy.getStyleClass().add("small");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(id);
            Clipboard.getSystemClipboard().setContent(content);
            copy.setText("Copiado");
            copyIcon.setIconLiteral("mdal-check");
        });

        HBox field = new HBox(10, value, copy);
        field.setAlignment(Pos.CENTER_LEFT);
        field.getStyleClass().add("about-id-field");
        return new VBox(6, caption, field);
    }

    private static List<License> licenses() {
        return Resources.text("/com/tunai/desktop/legal/third-party-licenses.txt").lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(AboutDialog::parse)
                .toList();
    }

    private static License parse(String line) {
        String[] parts = line.split(SEPARATOR);
        if (parts.length < 2) {
            return new License(line, "", null);
        }
        String last = parts[parts.length - 1];
        boolean hasUrl = last.startsWith("http");
        int end = hasUrl ? parts.length - 1 : parts.length;
        String terms = String.join(SEPARATOR, Arrays.copyOfRange(parts, 1, end));
        return new License(parts[0], terms, hasUrl ? last : null);
    }

    private static void browse(String url) {
        // Desktop.browse puede bloquear: fuera del hilo de JavaFX.
        Thread.ofVirtual().start(() -> {
            try {
                Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "No se pudo abrir " + url, e);
            }
        });
    }
}
