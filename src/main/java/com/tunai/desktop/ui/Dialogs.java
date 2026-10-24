package com.tunai.desktop.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

import java.util.Optional;

/** Diálogos con el estilo de la app y botones en español. */
public final class Dialogs {

    public static final ButtonType ACCEPT = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
    public static final ButtonType CANCEL = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

    private Dialogs() {
    }

    public static boolean confirm(Window owner, String title, String message, String confirmText) {
        ButtonType ok = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ok, CANCEL);
        prepare(alert, owner, title);
        return alert.showAndWait().filter(ok::equals).isPresent();
    }

    public static void info(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ACCEPT);
        prepare(alert, owner, title);
        alert.showAndWait();
    }

    public static void longText(Window owner, String title, String text) {
        Alert alert = new Alert(Alert.AlertType.NONE, null, ACCEPT);
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefSize(560, 360);
        alert.getDialogPane().setContent(area);
        prepare(alert, owner, title);
        alert.showAndWait();
    }

    public static Optional<String> textInput(Window owner, String title, String label, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setContentText(label);
        dialog.getDialogPane().getButtonTypes().setAll(ACCEPT, CANCEL);
        prepare(dialog, owner, title);
        return dialog.showAndWait().map(String::strip).filter(s -> !s.isEmpty());
    }

    /**
     * Pregunta qué hacer con los cambios sin guardar.
     *
     * @return {@code SAVE}, {@code DISCARD} o vacío si el usuario cancela
     */
    public static Optional<ButtonBar.ButtonData> unsavedChanges(Window owner) {
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Salir sin guardar", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.WARNING, "Tienes cambios sin guardar. ¿Qué quieres hacer?",
                save, discard, CANCEL);
        prepare(alert, owner, "Cambios sin guardar");
        return alert.showAndWait()
                .map(ButtonType::getButtonData)
                .filter(d -> d != ButtonBar.ButtonData.CANCEL_CLOSE);
    }

    static void prepare(Dialog<?> dialog, Window owner, String title) {
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.getDialogPane().getStylesheets().add(Resources.stylesheet());
    }
}
