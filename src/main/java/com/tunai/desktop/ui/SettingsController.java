package com.tunai.desktop.ui;

import atlantafx.base.controls.ToggleSwitch;
import com.tunai.desktop.App;
import com.tunai.desktop.AppContext;
import com.tunai.desktop.config.Settings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Ajustes de usuario: cuenta, créditos, privacidad, servidor, datos locales y versión. */
public class SettingsController implements NavigationAware {

    private static final Logger LOG = Logger.getLogger(SettingsController.class.getName());
    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("d 'de' MMMM, HH:mm", Locale.forLanguageTag("es-CO")).withZone(ZoneId.systemDefault());

    private final AppContext context;
    private final Label creditsValue = new Label();
    private final Label modelsValue = new Label();
    private final Label serverStatus = new Label();
    private final javafx.beans.value.ChangeListener<Object> listener = (o, a, b) -> refresh();

    @FXML
    private VBox page;

    public SettingsController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        page.getChildren().addAll(account(), credits(), privacy(), server(), data(), about());
        context.session().serverStatusProperty().addListener(listener);
        context.session().creditsProperty().addListener(listener);
        context.session().creditsPerModelProperty().addListener(listener);
        refresh();
    }

    @Override
    public void onLeave() {
        context.session().serverStatusProperty().removeListener(listener);
        context.session().creditsProperty().removeListener(listener);
        context.session().creditsPerModelProperty().removeListener(listener);
    }

    private void refresh() {
        int credits = context.session().getCredits();
        int perModel = context.session().getCreditsPerModel();
        creditsValue.setText(String.valueOf(credits));
        modelsValue.setText(String.valueOf(Credits.models(credits, perModel)));
        StatusIndicator.apply(serverStatus, context.session().getServerStatus());
    }

    private VBox account() {
        String username = context.username() != null ? context.username() : "Sin sesión";
        Label initial = new Label(username.substring(0, 1).toUpperCase());
        initial.getStyleClass().add("avatar-initial");
        StackPane avatar = new StackPane(initial);
        avatar.getStyleClass().add("avatar");

        Settings.Session s = context.settings().get().session;
        String until = s != null && s.expiresAt != null ? "Sesión activa hasta " + DATE.format(s.expiresAt) : "";
        Label name = new Label(username);
        name.getStyleClass().add("row-title");
        Label detail = new Label(until);
        detail.getStyleClass().addAll("muted", "small");
        VBox text = new VBox(2, name, detail);
        Button signOut = new Button("Cerrar sesión", new FontIcon("mdal-exit_to_app"));
        signOut.setOnAction(e -> context.signOut());
        HBox identity = new HBox(14, avatar, text);
        identity.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(identity, javafx.scene.layout.Priority.ALWAYS);
        HBox row = new HBox(16, identity, signOut);
        row.getStyleClass().add("settings-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return Kpi.section("Cuenta", row);
    }

    private VBox credits() {
        Label perModel = new Label();
        Label modelsCaption = new Label("modelos 3D que puedes generar");
        Label creditsCaption = new Label("créditos disponibles");
        HBox kpis = new HBox(16, Kpi.card("Saldo", creditsValue, creditsCaption),
                Kpi.card("Modelos", modelsValue, modelsCaption));
        kpis.setPadding(new javafx.geometry.Insets(16));
        perModel.setText("Cada modelo usa " + context.session().getCreditsPerModel() + " créditos");
        Button plans = new Button("Ver planes", new FontIcon("mdal-add"));
        plans.getStyleClass().add("accent");
        plans.setOnAction(e -> PricingDialog.show(context.stage(), context.session().getCreditsPerModel()));
        return Kpi.section("Créditos", kpis, Kpi.row("Planes de créditos", perModel.getText(), plans));
    }

    private VBox privacy() {
        ToggleSwitch consent = new ToggleSwitch();
        consent.setSelected(context.settings().get().hasConsent());
        consent.selectedProperty().addListener((o, was, now) -> {
            context.settings().get().consentAcceptedAt = now ? Instant.now() : null;
            context.settings().save();
        });
        return Kpi.section("Privacidad", Kpi.row("Enviar fotos a Tripo AI",
                "Necesario para generar modelos. TunAI no guarda tus fotos.", consent));
    }

    private VBox server() {
        serverStatus.getStyleClass().addAll("status-dot", "text-small");
        Label url = new Label(context.api().baseUrl().toString());
        url.getStyleClass().addAll("muted", "small");
        return Kpi.section("Servidor", Kpi.row("Estado de la conexión", null, serverStatus),
                Kpi.row("Dirección del servicio", null, url));
    }

    private VBox data() {
        Path garage = context.paths().garageDir();
        Button openGarage = new Button("Abrir carpeta", new FontIcon("mdal-folder_open"));
        openGarage.setOnAction(e -> open(garage));
        Button openLogs = new Button("Abrir carpeta", new FontIcon("mdal-folder_open"));
        openLogs.setOnAction(e -> open(context.paths().logsDir()));
        return Kpi.section("Datos en este equipo",
                Kpi.row("Garaje", garage.toString(), openGarage),
                Kpi.row("Registros", "Útiles si necesitas reportar un problema.", openLogs));
    }

    private VBox about() {
        Button info = new Button("Acerca de", new FontIcon("mdal-info"));
        info.setOnAction(e -> AboutDialog.show(context));
        return Kpi.section("TunAI", Kpi.row("Versión " + App.version(), "Licencias y créditos de terceros.", info));
    }

    private static void open(Path dir) {
        try {
            Files.createDirectories(dir);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo abrir " + dir, e);
        }
    }
}
