package com.tunai.desktop.ui;

import com.tunai.desktop.AppContext;
import com.tunai.desktop.api.Quota;
import com.tunai.desktop.garage.GarageVehicle;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Pantalla de inicio: el garaje local (§5.2). Funciona sin conexión. */
public class GarageController implements NavigationAware {

    private static final Logger LOG = Logger.getLogger(GarageController.class.getName());
    private static final DateTimeFormatter CARD_DATE =
            DateTimeFormatter.ofPattern("dd MMM", Locale.forLanguageTag("es-CO")).withZone(ZoneId.systemDefault());
    private static final double THUMB_SIZE = 180;

    private final AppContext context;
    private final ChangeListener<Object> statusListener = (obs, oldValue, newValue) -> refreshHeader();
    private final Tooltip newVehicleTooltip = new Tooltip();

    @FXML
    private Label serverStatusLabel;
    @FXML
    private Label quotaLabel;
    @FXML
    private StackPane newVehicleWrapper;
    @FXML
    private Button newVehicleButton;
    @FXML
    private ScrollPane cardsScroll;
    @FXML
    private FlowPane cardsPane;
    @FXML
    private Label emptyLabel;

    public GarageController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        newVehicleTooltip.setShowDelay(Duration.millis(200));
        context.session().serverStatusProperty().addListener(statusListener);
        context.session().quotaProperty().addListener(statusListener);
        refreshHeader();

        emptyLabel.visibleProperty().bind(Bindings.isEmpty(cardsPane.getChildren()));
        cardsScroll.visibleProperty().bind(emptyLabel.visibleProperty().not());
        reloadVehicles();
    }

    @Override
    public void onLeave() {
        context.session().serverStatusProperty().removeListener(statusListener);
        context.session().quotaProperty().removeListener(statusListener);
    }

    private void refreshHeader() {
        StatusIndicator.apply(serverStatusLabel, context.session().getServerStatus());
        Quota quota = context.session().getQuota();
        quotaLabel.setText(QuotaText.header(quota));

        String blockedReason = null;
        if (!context.settings().get().hasConsent()) {
            blockedReason = "Debes aceptar el envío de fotos para generar modelos.";
        } else if (quota != null && quota.exhausted()) {
            blockedReason = "Ya usaste tus generaciones de hoy. Se renuevan a las 00:00.";
        }
        newVehicleButton.setDisable(blockedReason != null);
        if (blockedReason != null) {
            newVehicleTooltip.setText(blockedReason);
            Tooltip.install(newVehicleWrapper, newVehicleTooltip);
        } else {
            Tooltip.uninstall(newVehicleWrapper, newVehicleTooltip);
        }
    }

    private void reloadVehicles() {
        List<GarageVehicle> vehicles = context.garage().list();
        cardsPane.getChildren().setAll(vehicles.stream().map(this::createCard).toList());
    }

    private VBox createCard(GarageVehicle vehicle) {
        ImageView thumb = new ImageView();
        thumb.setFitWidth(THUMB_SIZE);
        thumb.setFitHeight(THUMB_SIZE);
        thumb.setPreserveRatio(true);
        vehicle.thumbnail().ifPresent(p -> thumb.setImage(new Image(p.toUri().toString(), true)));
        StackPane thumbBox = new StackPane(thumb);
        thumbBox.setPrefSize(THUMB_SIZE, THUMB_SIZE);
        thumbBox.setStyle("-fx-background-color: #eef0f4; -fx-background-radius: 6;");
        if (vehicle.thumbnail().isEmpty()) {
            Label noThumb = new Label("Sin vista previa");
            noThumb.getStyleClass().addAll("muted", "small");
            thumbBox.getChildren().add(noThumb);
        }

        Label name = new Label(vehicle.name());
        name.getStyleClass().add("name");
        name.setMaxWidth(THUMB_SIZE);
        Label date = new Label(formatDate(vehicle));
        date.getStyleClass().addAll("muted", "small");

        VBox card = new VBox(8, thumbBox, name, date);
        card.getStyleClass().add("vehicle-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                openViewer(vehicle);
            }
        });

        MenuItem rename = new MenuItem("Renombrar");
        rename.setOnAction(e -> rename(vehicle));
        MenuItem export = new MenuItem("Exportar");
        export.setOnAction(e -> VehicleExport.exportGlb(context, vehicle));
        MenuItem delete = new MenuItem("Eliminar");
        delete.setOnAction(e -> delete(vehicle));
        ContextMenu menu = new ContextMenu(rename, export, new SeparatorMenuItem(), delete);
        card.setOnContextMenuRequested(e -> menu.show(card, e.getScreenX(), e.getScreenY()));
        return card;
    }

    private static String formatDate(GarageVehicle vehicle) {
        if (vehicle.metadata().createdAt == null) {
            return "";
        }
        return CARD_DATE.format(vehicle.metadata().createdAt).replace(".", "");
    }

    private void openViewer(GarageVehicle vehicle) {
        context.navigator().<ViewerController>goTo(Navigator.Screen.VIEWER, c -> c.open(vehicle));
    }

    private void rename(GarageVehicle vehicle) {
        Dialogs.textInput(context.stage(), "Renombrar", "Nombre:", vehicle.name()).ifPresent(newName -> {
            try {
                context.garage().rename(vehicle, newName);
                reloadVehicles();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "No se pudo renombrar " + vehicle.id(), e);
                Dialogs.info(context.stage(), "Renombrar", "No se pudo renombrar el vehículo.");
            }
        });
    }

    private void delete(GarageVehicle vehicle) {
        boolean confirmed = Dialogs.confirm(context.stage(), "Eliminar vehículo",
                "¿Eliminar \"" + vehicle.name() + "\" de tu garaje? Esta acción no se puede deshacer.", "Eliminar");
        if (!confirmed) {
            return;
        }
        try {
            context.garage().delete(vehicle);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo eliminar " + vehicle.id(), e);
            Dialogs.info(context.stage(), "Eliminar vehículo", "No se pudo eliminar el vehículo.");
        }
        reloadVehicles();
    }

    @FXML
    private void onNewVehicle() {
        context.navigator().goTo(Navigator.Screen.NEW_VEHICLE);
    }

    @FXML
    private void onAbout() {
        AboutDialog.show(context);
    }
}
