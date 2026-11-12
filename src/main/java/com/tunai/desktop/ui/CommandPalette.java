package com.tunai.desktop.ui;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Paleta de comandos (Ctrl+K): modal centrado de 560 px con búsqueda y resultados. Flechas para moverse, Enter ejecuta,
 * Esc o clic fuera cierran. Se superpone al marco de la app.
 */
final class CommandPalette extends StackPane {

    record Command(String group, String title, String icon, String hint, Runnable run) {
    }

    private static final double WIDTH = 560;
    private static final double ROW = 44;

    private final Supplier<List<Command>> source;
    private final TextField field = new TextField();
    private final ListView<Command> list = new ListView<>();
    private List<Command> all = List.of();

    CommandPalette(Supplier<List<Command>> source) {
        this.source = source;
        getStyleClass().add("palette-scrim");
        setVisible(false);
        setManaged(false);

        field.setPromptText("Busca una acción, una pantalla o un vehículo…");
        field.getStyleClass().add("palette-field");
        HBox top = new HBox(12, new FontIcon("mdmz-search"), field);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("palette-top");
        HBox.setHgrow(field, Priority.ALWAYS);

        list.getStyleClass().add("palette-list");
        list.setFixedCellSize(ROW);
        list.setFocusTraversable(false);
        list.setPlaceholder(new Label("Sin resultados"));
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Command c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setGraphic(null);
                    return;
                }
                Label title = new Label(c.title());
                title.getStyleClass().add("palette-title");
                Region grow = new Region();
                HBox.setHgrow(grow, Priority.ALWAYS);
                Label hint = new Label(c.hint());
                hint.getStyleClass().add("palette-hint");
                HBox row = new HBox(12, new FontIcon(c.icon()), title, grow, hint);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });

        Label footer = new Label("↑ ↓ para moverte   ·   Enter para elegir   ·   Esc para cerrar");
        footer.getStyleClass().add("palette-footer");

        VBox card = new VBox(top, list, footer);
        card.getStyleClass().add("palette-card");
        card.setMaxWidth(WIDTH);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        StackPane.setMargin(card, new Insets(96, 24, 24, 24));
        getChildren().add(card);

        field.textProperty().addListener((o, a, b) -> filter());
        field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case DOWN -> move(1);
                case UP -> move(-1);
                case ENTER -> runSelected();
                default -> {
                    return;
                }
            }
            e.consume();
        });
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 1 && list.getSelectionModel().getSelectedItem() != null) {
                runSelected();
            }
        });
        // Clic en el fondo oscurecido (no en la tarjeta) cierra.
        addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() == this) {
                close();
            }
        });
    }

    boolean isOpen() {
        return isVisible();
    }

    void toggle() {
        if (isOpen()) {
            close();
        } else {
            open();
        }
    }

    void open() {
        all = source.get();
        field.clear();
        filter();
        setManaged(true);
        setVisible(true);
        FadeTransition fade = new FadeTransition(Duration.millis(140), this);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
        field.requestFocus();
    }

    void close() {
        setVisible(false);
        setManaged(false);
    }

    private void filter() {
        String q = normalize(field.getText());
        List<Command> found = all.stream()
                .filter(c -> q.isEmpty() || normalize(c.title()).contains(q) || normalize(c.hint()).contains(q))
                .toList();
        list.getItems().setAll(found);
        list.setPrefHeight(Math.max(ROW, Math.min(found.size(), 7) * ROW) + 16);
        if (!found.isEmpty()) {
            list.getSelectionModel().selectFirst();
        }
    }

    private void move(int delta) {
        int size = list.getItems().size();
        if (size == 0) {
            return;
        }
        int next = Math.floorMod(list.getSelectionModel().getSelectedIndex() + delta, size);
        list.getSelectionModel().select(next);
        list.scrollTo(next);
    }

    private void runSelected() {
        Command c = list.getSelectionModel().getSelectedItem();
        if (c == null) {
            return;
        }
        close();
        c.run().run();
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
