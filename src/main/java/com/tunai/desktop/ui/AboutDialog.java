package com.tunai.desktop.ui;

import com.tunai.desktop.App;
import com.tunai.desktop.AppContext;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Acerca de y créditos (§5.6). */
final class AboutDialog {

    private AboutDialog() {
    }

    static void show(AppContext context) {
        Label title = new Label(App.NAME + " " + App.version());
        title.getStyleClass().add("subtitle");

        // Texto exacto de atribución exigido por la licencia de Stability AI: PENDIENTE DE VALIDAR (§10).
        Label credit = new Label(Resources.text("/com/tunai/desktop/legal/attribution.txt").strip());
        credit.setWrapText(true);

        TextArea licenses = new TextArea(Resources.text("/com/tunai/desktop/legal/third-party-licenses.txt"));
        licenses.setEditable(false);
        licenses.setWrapText(true);
        licenses.setPrefRowCount(10);

        String id = context.installationId().value().toString();
        Label idLabel = new Label("ID de instalación: " + id);
        idLabel.getStyleClass().addAll("muted", "small");
        Button copy = new Button("Copiar");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(id);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            copy.setText("Copiado");
        });
        HBox idRow = new HBox(10, idLabel, copy);
        idRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox content = new VBox(12, title, credit, new Label("Licencias"), licenses, idRow);
        content.setPadding(new Insets(8));
        content.setPrefWidth(560);

        Alert alert = new Alert(Alert.AlertType.NONE, null, Dialogs.ACCEPT);
        alert.getDialogPane().setContent(content);
        Dialogs.prepare(alert, context.stage(), "Acerca de TunAI");
        alert.showAndWait();
    }
}
