package com.tunai.desktop.ui;

import com.tunai.desktop.AppContext;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;

import java.time.Instant;

/** Primer inicio: bienvenida y consentimiento (§5.1). */
public class ConsentController {

    private final AppContext context;

    @FXML
    private Label summaryLabel;
    @FXML
    private CheckBox acceptCheck;
    @FXML
    private Button startButton;

    public ConsentController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        summaryLabel.setText(Resources.text("/com/tunai/desktop/legal/consent-summary.txt").strip());
        startButton.disableProperty().bind(acceptCheck.selectedProperty().not());
    }

    @FXML
    private void onShowDetails() {
        Dialogs.longText(context.stage(), "Uso de tus fotos",
                Resources.text("/com/tunai/desktop/legal/consent-details.txt"));
    }

    @FXML
    private void onStart() {
        context.settings().get().consentAcceptedAt = Instant.now();
        context.settings().save();
        context.navigator().goTo(Navigator.Screen.GARAGE);
    }
}
