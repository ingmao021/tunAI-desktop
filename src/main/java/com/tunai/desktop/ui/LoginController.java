package com.tunai.desktop.ui;

import com.tunai.desktop.AppContext;
import com.tunai.desktop.api.ApiException;
import com.tunai.desktop.api.AuthSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Inicio de sesión: valida los campos, llama al backend y, si acepta, entra a la app. */
public class LoginController implements NavigationAware {

    private static final Logger LOG = Logger.getLogger(LoginController.class.getName());

    private final AppContext context;
    private boolean busy;
    private boolean left;

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordVisibleField;
    @FXML
    private Button toggleButton;
    @FXML
    private FontIcon toggleIcon;
    @FXML
    private HBox errorBox;
    @FXML
    private Label errorLabel;
    @FXML
    private Button loginButton;
    @FXML
    private ProgressIndicator spinner;
    @FXML
    private Label hintLabel;

    public LoginController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        // El campo visible y el oculto comparten el texto: alternar no pierde lo escrito.
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        usernameField.textProperty().addListener((obs, old, text) -> clearError());
        passwordField.textProperty().addListener((obs, old, text) -> clearError());
        String remembered = context.settings().get().account;
        if (remembered != null) {
            usernameField.setText(remembered);
        }
        Platform.runLater(() -> (usernameField.getText().isBlank() ? usernameField : passwordField).requestFocus());
    }

    @Override
    public void onLeave() {
        left = true;
    }

    @FXML
    private void onTogglePassword() {
        boolean show = !passwordVisibleField.isVisible();
        passwordVisibleField.setVisible(show);
        passwordVisibleField.setManaged(show);
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        toggleIcon.setIconLiteral(show ? "mdmz-visibility_off" : "mdmz-visibility");
        (show ? passwordVisibleField : passwordField).requestFocus();
        (show ? passwordVisibleField : passwordField).end();
    }

    @FXML
    private void onLogin() {
        if (busy) {
            return;
        }
        String username = usernameField.getText().strip();
        String password = passwordField.getText();
        if (username.isEmpty()) {
            showError("Ingresa tu correo o usuario.");
            usernameField.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            showError("Ingresa tu contraseña.");
            passwordField.requestFocus();
            return;
        }
        setBusy(true);
        context.background().execute(() -> authenticate(username, password));
    }

    private void authenticate(String username, String password) {
        try {
            AuthSession session = context.api().login(username, password);
            Platform.runLater(() -> succeed(session));
        } catch (ApiException e) {
            LOG.log(Level.INFO, "Inicio de sesión rechazado: " + e.getMessage());
            Platform.runLater(() -> fail(e.userMessage()));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Fallo inesperado al iniciar sesión", e);
            Platform.runLater(() -> fail("Ocurrió un error inesperado."));
        }
    }

    private void succeed(AuthSession session) {
        if (left) {
            return;
        }
        passwordField.clear();
        context.signIn(session);
        context.navigator().goTo(Navigator.afterLogin(context.settings().get()));
    }

    private void fail(String message) {
        if (left) {
            return;
        }
        setBusy(false);
        showError(message);
        passwordField.requestFocus();
        passwordField.selectAll();
    }

    private void setBusy(boolean value) {
        busy = value;
        spinner.setVisible(value);
        spinner.setManaged(value);
        hintLabel.setVisible(value);
        hintLabel.setManaged(value);
        loginButton.setText(value ? "Iniciando sesión…" : "Iniciar sesión");
        loginButton.setDisable(value);
        usernameField.setDisable(value);
        passwordField.setDisable(value);
        passwordVisibleField.setDisable(value);
        toggleButton.setDisable(value);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorBox.setVisible(true);
        errorBox.setManaged(true);
    }

    private void clearError() {
        errorBox.setVisible(false);
        errorBox.setManaged(false);
    }
}
