package com.tunai.desktop.ui;

import javafx.scene.control.Label;

/** Indicador del servidor (§5.2). */
final class StatusIndicator {

    private static final String[] CLASSES = {"status-connecting", "status-ok", "status-error"};

    private StatusIndicator() {
    }

    static void apply(Label label, SessionState.ServerStatus status) {
        label.getStyleClass().removeAll(CLASSES);
        switch (status) {
            case CONNECTING -> {
                label.setText("○ Conectando…");
                label.getStyleClass().add("status-connecting");
            }
            case READY -> {
                label.setText("● Servidor listo");
                label.getStyleClass().add("status-ok");
            }
            case OFFLINE -> {
                label.setText("● Sin conexión");
                label.getStyleClass().add("status-error");
            }
        }
    }
}
