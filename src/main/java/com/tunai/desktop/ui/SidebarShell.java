package com.tunai.desktop.ui;

import com.tunai.desktop.AppContext;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Marco de la app: sidebar carbón flotante, compacto (72 px, solo iconos + tooltip) o expandido (240 px, con etiquetas),
 * y el contenido de la pantalla en un lienzo blanco. Es la única superficie oscura permitida por el diseño.
 * Ctrl+B alterna el ancho y la elección se recuerda en los ajustes.
 */
final class SidebarShell extends StackPane {

    static final double COLLAPSED = 72;
    static final double EXPANDED = 240;

    private final AppContext context;
    private final Map<Navigator.Screen, Button> screens = new EnumMap<>(Navigator.Screen.class);
    private final List<Button> buttons = new ArrayList<>();
    private final List<Label> groupLabels = new ArrayList<>();
    private final DoubleProperty width = new SimpleDoubleProperty(COLLAPSED);
    private final VBox sidebar = new VBox(6);
    private final Button toggle = new Button();
    private final Label brand = new Label("TunAI");
    private final Label userName = new Label();
    private final HBox userChip = new HBox(10);
    private final BorderPane frame = new BorderPane();
    private final CommandPalette palette = new CommandPalette(this::commands);
    /** Etiquetas visibles: sigue a la animación (aparecen al abrir, se ocultan al terminar de cerrar). */
    private boolean textShown;

    SidebarShell(Parent content, Navigator.Screen active, AppContext context) {
        this.context = context;
        getStyleClass().add("app-shell");
        getChildren().addAll(frame, palette);
        boolean expanded = context.settings().get().sidebarExpanded;
        textShown = expanded;
        width.set(expanded ? EXPANDED : COLLAPSED);

        sidebar.getStyleClass().add("sidebar");
        sidebar.minWidthProperty().bind(width);
        sidebar.prefWidthProperty().bind(width);
        sidebar.maxWidthProperty().bind(width);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(sidebar.widthProperty());
        clip.heightProperty().bind(sidebar.heightProperty());
        sidebar.setClip(clip);

        buildHeader();
        sidebar.getChildren().add(group("Menú"));
        sidebar.getChildren().add(item("mdmz-search", "Buscar  (Ctrl+K)", palette::open));
        sidebar.getChildren().add(screenItem(Navigator.Screen.GARAGE, "mdal-directions_car", "Mi garaje"));
        sidebar.getChildren().add(item("mdal-add", "Nuevo vehículo", this::newVehicle));
        sidebar.getChildren().add(item("mdal-folder_open", "Abrir modelo", this::openModel));
        sidebar.getChildren().add(item("mdal-credit_card", "Créditos y planes", this::plans));
        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        sidebar.getChildren().add(grow);
        sidebar.getChildren().add(group("Cuenta"));
        sidebar.getChildren().add(screenItem(Navigator.Screen.SETTINGS, "mdmz-settings", "Ajustes"));
        sidebar.getChildren().add(item("mdal-help_outline", "Acerca de TunAI", () -> AboutDialog.show(context)));
        sidebar.getChildren().add(item("mdal-exit_to_app", "Cerrar sesión", context::signOut));
        buildUserChip();

        BorderPane.setMargin(sidebar, new Insets(12, 0, 12, 12));
        frame.setLeft(sidebar);
        StackPane canvas = new StackPane(content);
        canvas.getStyleClass().add("canvas");
        Rectangle canvasClip = new Rectangle();
        canvasClip.setArcWidth(48);
        canvasClip.setArcHeight(48);
        canvasClip.widthProperty().bind(canvas.widthProperty());
        canvasClip.heightProperty().bind(canvas.heightProperty());
        canvas.setClip(canvasClip);
        BorderPane.setMargin(canvas, new Insets(12));
        frame.setCenter(canvas);

        screens.forEach((screen, b) -> {
            if (screen == active) {
                b.getStyleClass().add("active");
            }
        });
        applyMode();
        sceneProperty().addListener((o, old, scene) -> {
            if (scene != null) {
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN),
                        this::toggle);
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
                        palette::toggle);
            }
        });
    }

    private void buildHeader() {
        toggle.setGraphic(new FontIcon("mdmz-menu"));
        toggle.getStyleClass().addAll("sidebar-item", "sidebar-toggle");
        toggle.setAccessibleText("Expandir o contraer el menú");
        toggle.setOnAction(e -> toggle());
        track(toggle, "Expandir menú (Ctrl+B)");
        brand.getStyleClass().add("sidebar-brand");
        HBox header = new HBox(10, toggle, brand);
        header.setAlignment(Pos.CENTER_LEFT);
        sidebar.getChildren().add(header);
    }

    private void buildUserChip() {
        String name = context.username() != null ? context.username() : "Sin sesión";
        Label initial = new Label(name.substring(0, 1).toUpperCase());
        initial.getStyleClass().add("avatar-initial");
        StackPane avatar = new StackPane(initial);
        avatar.getStyleClass().addAll("avatar", "avatar-small");
        userName.setText(name);
        userName.getStyleClass().add("sidebar-user");
        userChip.getChildren().addAll(avatar, userName);
        userChip.getStyleClass().add("sidebar-user-chip");
        userChip.setOnMouseClicked(e -> context.navigator().goTo(Navigator.Screen.SETTINGS));
        Tooltip tip = new Tooltip(name + " · Ajustes");
        tip.getStyleClass().add("sidebar-tooltip");
        Tooltip.install(userChip, tip);
        sidebar.getChildren().add(userChip);
    }

    private Label group(String text) {
        Label l = new Label(text.toUpperCase());
        l.getStyleClass().add("sidebar-group");
        groupLabels.add(l);
        return l;
    }

    private Button screenItem(Navigator.Screen screen, String icon, String label) {
        Button b = item(icon, label, () -> context.navigator().goTo(screen));
        screens.put(screen, b);
        return b;
    }

    private Button item(String icon, String label, Runnable run) {
        Button b = new Button(label);
        b.setGraphic(new FontIcon(icon));
        b.getStyleClass().add("sidebar-item");
        b.setAccessibleText(label);
        b.setOnAction(e -> run.run());
        track(b, label);
        return b;
    }

    private void track(Button b, String tooltip) {
        Tooltip tip = new Tooltip(tooltip);
        tip.setShowDelay(Duration.millis(300));
        tip.getStyleClass().add("sidebar-tooltip");
        b.setUserData(tip);
        buttons.add(b);
    }

    void toggle() {
        boolean expand = width.get() < EXPANDED / 2 + COLLAPSED / 2;
        context.settings().get().sidebarExpanded = expand;
        context.settings().save();
        Timeline t = new Timeline(new KeyFrame(Duration.millis(220),
                new KeyValue(width, expand ? EXPANDED : COLLAPSED, Interpolator.SPLINE(.2, 0, 0, 1))));
        if (expand) {
            textShown = true;
            applyMode();
        } else {
            // El texto se oculta al terminar la animación para que no se corte a mitad.
            t.setOnFinished(e -> {
                textShown = false;
                applyMode();
            });
        }
        t.play();
    }

    /** Etiquetas e inscripciones solo en modo expandido; los tooltips solo en el compacto. */
    private void applyMode() {
        for (Button b : buttons) {
            boolean isToggle = b == toggle;
            boolean wide = textShown && !isToggle;
            b.setContentDisplay(wide ? ContentDisplay.LEFT : ContentDisplay.GRAPHIC_ONLY);
            b.setAlignment(wide ? Pos.CENTER_LEFT : Pos.CENTER);
            b.setMaxWidth(wide ? Double.MAX_VALUE : 44);
            b.setPrefWidth(wide ? Region.USE_COMPUTED_SIZE : 44);
            if (b.getUserData() instanceof Tooltip tip) {
                b.setTooltip(wide ? null : tip);
            }
        }
        for (Label l : groupLabels) {
            l.setVisible(textShown);
            l.setManaged(textShown);
        }
        brand.setVisible(textShown);
        brand.setManaged(textShown);
        userName.setVisible(textShown);
        userName.setManaged(textShown);
        sidebar.setAlignment(textShown ? Pos.TOP_LEFT : Pos.TOP_CENTER);
        sidebar.setPadding(textShown ? new Insets(16, 14, 16, 14) : new Insets(16, 0, 16, 0));
        userChip.setAlignment(textShown ? Pos.CENTER_LEFT : Pos.CENTER);
        ((HBox) toggle.getParent()).setAlignment(textShown ? Pos.CENTER_LEFT : Pos.CENTER);
    }

    /** Comandos de la paleta: navegación, acciones y los vehículos del garaje. */
    private java.util.List<CommandPalette.Command> commands() {
        List<CommandPalette.Command> list = new ArrayList<>(List.of(
                new CommandPalette.Command("Ir a", "Mi garaje", "mdal-directions_car", "Pantalla",
                        () -> context.navigator().goTo(Navigator.Screen.GARAGE)),
                new CommandPalette.Command("Ir a", "Ajustes", "mdmz-settings", "Pantalla",
                        () -> context.navigator().goTo(Navigator.Screen.SETTINGS)),
                new CommandPalette.Command("Acción", "Nuevo vehículo", "mdal-add", "Acción", this::newVehicle),
                new CommandPalette.Command("Acción", "Abrir modelo desde el disco", "mdal-folder_open", "Acción",
                        this::openModel),
                new CommandPalette.Command("Acción", "Ver créditos y planes", "mdal-credit_card", "Acción",
                        this::plans),
                new CommandPalette.Command("Acción", "Expandir o contraer el menú", "mdmz-menu", "Ctrl+B",
                        this::toggle),
                new CommandPalette.Command("Acción", "Acerca de TunAI", "mdal-help_outline", "Acción",
                        () -> AboutDialog.show(context)),
                new CommandPalette.Command("Acción", "Cerrar sesión", "mdal-exit_to_app", "Acción",
                        context::signOut)));
        for (var vehicle : context.garage().list()) {
            list.add(new CommandPalette.Command("Vehículo", vehicle.name(), "mdal-directions_car", "Vehículo",
                    () -> context.navigator().<ViewerController>goTo(Navigator.Screen.VIEWER, c -> c.open(vehicle))));
        }
        return list;
    }

    private void newVehicle() {
        if (!context.settings().get().hasConsent()) {
            Dialogs.info(context.stage(), "Nuevo vehículo",
                    "Debes aceptar el envío de fotos para generar modelos. Puedes hacerlo en Ajustes → Privacidad.");
            context.navigator().goTo(Navigator.Screen.SETTINGS);
            return;
        }
        if (!context.session().canGenerate()) {
            PricingDialog.show(context.stage(), context.session().getCreditsPerModel());
            return;
        }
        context.navigator().goTo(Navigator.Screen.NEW_VEHICLE);
    }

    private void openModel() {
        context.navigator().<GarageController>goTo(Navigator.Screen.GARAGE, c -> Platform.runLater(c::onOpenModel));
    }

    private void plans() {
        PricingDialog.show(context.stage(), context.session().getCreditsPerModel());
    }

    /** Pantallas que se muestran dentro del marco; login y consentimiento ocupan toda la ventana. */
    static boolean wraps(Navigator.Screen screen) {
        return screen == Navigator.Screen.GARAGE
                || screen == Navigator.Screen.NEW_VEHICLE
                || screen == Navigator.Screen.GENERATING
                || screen == Navigator.Screen.VIEWER
                || screen == Navigator.Screen.SETTINGS;
    }
}
