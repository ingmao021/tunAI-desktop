package com.tunai.desktop.ui;

import atlantafx.base.controls.Card;
import atlantafx.base.controls.Tile;
import atlantafx.base.theme.Styles;
import com.tunai.desktop.AppContext;
import com.tunai.desktop.api.Quota;
import com.tunai.desktop.garage.GarageVehicle;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Pantalla de inicio: el garaje local (§5.2). Funciona sin conexión. */
public class GarageController implements NavigationAware {

    private static final Logger LOG = Logger.getLogger(GarageController.class.getName());
    private static final DateTimeFormatter CARD_DATE =
            DateTimeFormatter.ofPattern("dd MMM", Locale.forLanguageTag("es-CO")).withZone(ZoneId.systemDefault());
    private static final double THUMB_SIZE = 200;
    private static final double CARD_WIDTH = 232;

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
    private Label countLabel;
    @FXML
    private VBox emptyState;
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
        context.session().creditsProperty().addListener(statusListener);
        context.session().creditsPerModelProperty().addListener(statusListener);
        refreshHeader();
        if (context.session().getServerStatus() == SessionState.ServerStatus.READY) {
            context.serverMonitor().refreshQuota();
        }

        BooleanBinding empty = Bindings.isEmpty(cardsPane.getChildren());
        emptyState.visibleProperty().bind(empty);
        emptyLabel.visibleProperty().bind(empty);
        cardsScroll.visibleProperty().bind(empty.not());
        reloadVehicles();
    }

    @Override
    public void onLeave() {
        context.session().serverStatusProperty().removeListener(statusListener);
        context.session().quotaProperty().removeListener(statusListener);
        context.session().creditsProperty().removeListener(statusListener);
        context.session().creditsPerModelProperty().removeListener(statusListener);
    }

    private void refreshHeader() {
        StatusIndicator.apply(serverStatusLabel, context.session().getServerStatus());
        Quota quota = context.session().getQuota();
        quotaLabel.setText(Credits.header(context.session().getCredits()));
        quotaLabel.setGraphic(Credits.icon(16));

        // Sin créditos el botón sigue activo: al pulsarlo se ofrecen los planes (la cuota también llega en cero).
        String blockedReason = null;
        if (!context.settings().get().hasConsent()) {
            blockedReason = "Debes aceptar el envío de fotos para generar modelos.";
        } else if (Credits.dailyLimitReached(quota, context.session().getCredits(),
                context.session().getCreditsPerModel())) {
            blockedReason = Credits.dailyLimitMessage(quota);
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
        countLabel.setText(switch (vehicles.size()) {
            case 0 -> "";
            case 1 -> "1 vehículo";
            default -> vehicles.size() + " vehículos";
        });
    }

    private Card createCard(GarageVehicle vehicle) {
        ImageView thumb = new ImageView();
        thumb.setFitWidth(THUMB_SIZE);
        thumb.setFitHeight(THUMB_SIZE);
        thumb.setPreserveRatio(true);
        vehicle.thumbnail().ifPresent(p -> thumb.setImage(new Image(p.toUri().toString(), true)));
        StackPane thumbBox = new StackPane(thumb);
        thumbBox.setPrefSize(THUMB_SIZE, THUMB_SIZE);
        thumbBox.getStyleClass().add("thumb-box");
        if (vehicle.thumbnail().isEmpty()) {
            Label noThumb = new Label("Sin vista previa", new FontIcon("mdal-directions_car"));
            noThumb.setContentDisplay(ContentDisplay.TOP);
            noThumb.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
            thumbBox.getChildren().add(noThumb);
        }
        paintChip(vehicle).ifPresent(chip -> {
            StackPane.setAlignment(chip, Pos.TOP_RIGHT);
            StackPane.setMargin(chip, new Insets(8));
            thumbBox.getChildren().add(chip);
        });

        // Las mismas acciones que el clic derecho, pero visibles.
        MenuButton actions = new MenuButton(null, new FontIcon("mdmz-more_horiz"));
        actions.getItems().setAll(actionItems(vehicle));
        actions.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, "no-arrow");

        String date = formatDate(vehicle);
        Tile info = new Tile(vehicle.name(), date.isEmpty() ? null : date);
        info.setAction(actions);

        Card card = new Card();
        card.getStyleClass().addAll("vehicle-card", Styles.ELEVATED_1, Styles.INTERACTIVE);
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        card.setSubHeader(thumbBox);
        card.setBody(info);
        // Filtro (no handler): el clic debe llegar aunque lo reciba un nodo interno del Card o del Tile.
        card.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && !isInside(e.getPickResult().getIntersectedNode(), actions)) {
                openViewer(vehicle);
            }
        });

        ContextMenu menu = new ContextMenu();
        menu.getItems().setAll(actionItems(vehicle));
        card.setOnContextMenuRequested(e -> menu.show(card, e.getScreenX(), e.getScreenY()));
        return card;
    }

    /** Chip con el color aplicado, para reconocer el vehículo de un vistazo. */
    private static Optional<Label> paintChip(GarageVehicle vehicle) {
        if (vehicle.metadata().paint == null || vehicle.metadata().paint.color == null) {
            return Optional.empty();
        }
        try {
            Label chip = new Label("Pintado", new Circle(5, Color.web(vehicle.metadata().paint.color)));
            chip.getStyleClass().add("card-chip");
            return Optional.of(chip);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }

    /** Un MenuItem solo puede estar en un menú, así que cada menú pide los suyos. */
    private List<MenuItem> actionItems(GarageVehicle vehicle) {
        MenuItem rename = new MenuItem("Renombrar", new FontIcon("mdal-edit"));
        rename.setOnAction(e -> rename(vehicle));
        MenuItem export = new MenuItem("Exportar", new FontIcon("mdal-get_app"));
        export.setOnAction(e -> VehicleExport.exportGlb(context, vehicle));
        MenuItem delete = new MenuItem("Eliminar", new FontIcon("mdal-delete"));
        delete.getStyleClass().add(Styles.DANGER);
        delete.setOnAction(e -> delete(vehicle));
        return List.of(rename, export, new SeparatorMenuItem(), delete);
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
        if (!context.session().canGenerate()) {
            PricingDialog.show(context.stage(), context.session().getCreditsPerModel());
            return;
        }
        context.navigator().goTo(Navigator.Screen.NEW_VEHICLE);
    }

    @FXML
    private void onAbout() {
        AboutDialog.show(context);
    }
}
