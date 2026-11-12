package com.tunai.desktop.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Piezas de maquetación del diseño: tarjeta KPI, sección con etiqueta en mayúsculas y fila de ajuste. */
final class Kpi {

    private Kpi() {
    }

    /** Tarjeta con etiqueta en mayúsculas, valor grande y una línea de apoyo. */
    static VBox card(String overline, Label value, Label caption) {
        Label over = new Label(overline.toUpperCase());
        over.getStyleClass().add("overline");
        value.getStyleClass().add("kpi-value");
        caption.getStyleClass().addAll("muted", "small");
        VBox box = new VBox(4, over, value, caption);
        box.getStyleClass().add("kpi-card");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** Bloque de ajustes: etiqueta de sección encima y una tarjeta con filas separadas por divisores. */
    static VBox section(String overline, Node... rows) {
        Label over = new Label(overline.toUpperCase());
        over.getStyleClass().add("overline");
        VBox card = new VBox();
        card.getStyleClass().add("settings-card");
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                VBox divider = new VBox();
                divider.getStyleClass().add("divider");
                card.getChildren().add(divider);
            }
            card.getChildren().add(rows[i]);
        }
        VBox section = new VBox(8, over, card);
        section.setPadding(new Insets(0));
        return section;
    }

    /** Fila: título y descripción a la izquierda, control a la derecha. */
    static HBox row(String title, String description, Node control) {
        Label t = new Label(title);
        t.getStyleClass().add("row-title");
        VBox text = new VBox(2, t);
        if (description != null && !description.isBlank()) {
            Label d = new Label(description);
            d.getStyleClass().addAll("muted", "small");
            d.setWrapText(true);
            text.getChildren().add(d);
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(16, text);
        row.getStyleClass().add("settings-row");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        if (control != null) {
            row.getChildren().add(control);
        }
        return row;
    }
}
