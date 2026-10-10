package com.tunai.desktop.ui;

import com.tunai.desktop.api.ApiException;
import com.tunai.desktop.api.ErrorCode;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

/** Muestra un error del catálogo §7 con su acción y un enlace "Detalles" con el {@code traceId}. */
public final class ErrorDialog {

    public enum Choice { RETRY, CLOSE }

    private ErrorDialog() {
    }

    public static Choice show(Window owner, ApiException error) {
        ErrorCode code = error.code();
        Label message = new Label(error.userMessage());
        message.setWrapText(true);
        message.setMaxWidth(440);

        Label details = new Label(details(error));
        details.getStyleClass().addAll("muted", "small");
        details.setWrapText(true);
        details.setMaxWidth(440);
        details.setVisible(false);
        details.setManaged(false);
        Hyperlink toggle = new Hyperlink("Detalles");
        toggle.getStyleClass().add("small");
        toggle.setOnAction(e -> {
            details.setVisible(!details.isVisible());
            details.setManaged(details.isVisible());
            toggle.getScene().getWindow().sizeToScene();
        });

        VBox content = new VBox(10, message, toggle, details);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.getDialogPane().setContent(content);

        ButtonType retry = new ButtonType("Reintentar", ButtonBar.ButtonData.OK_DONE);
        boolean canRetry = code.action() == ErrorCode.Action.RETRY || code.action() == ErrorCode.Action.RETRY_COUNTDOWN;
        if (canRetry) {
            ButtonType back = new ButtonType("Volver al garaje", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(retry, back);
        } else {
            alert.getButtonTypes().setAll(Dialogs.ACCEPT);
        }
        Dialogs.prepare(alert, owner, "TunAI");

        Timeline countdown = null;
        if (code.action() == ErrorCode.Action.RETRY_COUNTDOWN && error.retryAfterSeconds() != null
                && error.retryAfterSeconds() > 0) {
            countdown = startCountdown((Button) alert.getDialogPane().lookupButton(retry), error.retryAfterSeconds());
        }
        Choice choice = alert.showAndWait().filter(retry::equals).map(b -> Choice.RETRY).orElse(Choice.CLOSE);
        if (countdown != null) {
            countdown.stop();
        }
        return choice;
    }

    private static Timeline startCountdown(Button retryButton, int seconds) {
        int[] left = {seconds};
        retryButton.setDisable(true);
        retryButton.setText("Reintentar (" + left[0] + ")");
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), (ActionEvent e) -> {
            left[0]--;
            if (left[0] <= 0) {
                retryButton.setText("Reintentar");
                retryButton.setDisable(false);
            } else {
                retryButton.setText("Reintentar (" + left[0] + ")");
            }
        }));
        timeline.setCycleCount(seconds);
        timeline.play();
        return timeline;
    }

    private static String details(ApiException error) {
        StringBuilder sb = new StringBuilder("Código: ").append(error.rawCode() != null ? error.rawCode() : error.code());
        if (error.httpStatus() > 0) {
            sb.append(" · HTTP ").append(error.httpStatus());
        }
        sb.append("\ntraceId: ").append(error.traceId() != null ? error.traceId() : "(no disponible)");
        return sb.toString();
    }
}
