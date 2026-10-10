package com.tunai.desktop.ui;

import com.tunai.desktop.AppContext;
import com.tunai.desktop.api.ApiException;
import com.tunai.desktop.api.ErrorCode;
import com.tunai.desktop.garage.GarageVehicle;
import com.tunai.desktop.garage.VehicleMetadata;
import com.tunai.desktop.image.FxImages;
import com.tunai.desktop.paint.DominantColorMaskStrategy;
import com.tunai.desktop.paint.PaintMaskStrategy;
import com.tunai.desktop.paint.Recolorer;
import com.tunai.desktop.paint.UvCoverage;
import com.tunai.desktop.viewer.GlbModel;
import com.tunai.desktop.viewer.ModelViewer;
import com.tunai.desktop.viewer.UnsupportedModelException;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Visor / Editor (§5.5). */
public class ViewerController implements NavigationAware {

    private static final Logger LOG = Logger.getLogger(ViewerController.class.getName());

    /** Paleta de 8 colores. */
    static final String[][] PALETTE = {
            {"Rojo", "#C62828"}, {"Azul", "#1565C0"}, {"Negro", "#212121"}, {"Blanco", "#F5F5F5"},
            {"Plata", "#B0B3B8"}, {"Verde", "#2E7D32"}, {"Amarillo", "#F9A825"}, {"Naranja", "#EF6C00"},
    };
    static final double DEFAULT_INTENSITY = 0.85;

    private final AppContext context;
    private final PaintMaskStrategy maskStrategy = new DominantColorMaskStrategy();
    private final ModelViewer viewer = new ModelViewer();
    private final ToggleGroup paletteGroup = new ToggleGroup();
    private final ToggleGroup compareGroup = new ToggleGroup();

    @FXML
    private Label titleLabel;
    @FXML
    private Label unsavedLabel;
    @FXML
    private Button saveButton;
    @FXML
    private StackPane viewerHost;
    @FXML
    private ProgressIndicator loadingIndicator;
    @FXML
    private HBox viewerToolbar;
    @FXML
    private ToggleButton autoRotateToggle;
    @FXML
    private FlowPane palette;
    @FXML
    private ColorPicker customColor;
    @FXML
    private Slider intensitySlider;
    @FXML
    private Button restoreButton;
    @FXML
    private ProgressIndicator paintingIndicator;
    @FXML
    private Label paintStatus;
    @FXML
    private ToggleButton beforeToggle;
    @FXML
    private ToggleButton afterToggle;

    private GarageVehicle vehicle;
    private byte[] glbBytes;
    private Image originalTexture;
    private int textureWidth;
    private int textureHeight;
    private Recolorer recolorer;

    /** Textura que se ve en "Después": la recoloreada, o la original si no hay color. */
    private Image currentTexture;
    /** ARGB de la textura recoloreada; nulo si se ve el color original. */
    private int[] editedPixels;
    private String currentColor;
    private double currentIntensity = DEFAULT_INTENSITY;
    private String savedPaintKey;

    private boolean recoloring;
    private boolean recolorPending;
    private boolean saving;
    private boolean updatingControls;

    public ViewerController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        viewerHost.getChildren().add(0, viewer);
        viewer.setVisible(false);
        viewerToolbar.setVisible(false);
        viewer.autoRotateProperty().bind(autoRotateToggle.selectedProperty());

        for (String[] entry : PALETTE) {
            ToggleButton swatch = new ToggleButton();
            swatch.getStyleClass().add("swatch");
            swatch.setStyle("-fx-background-color: " + entry[1] + ";");
            swatch.setUserData(entry[1]);
            swatch.setTooltip(new Tooltip(entry[0]));
            swatch.setToggleGroup(paletteGroup);
            swatch.setOnAction(e -> {
                if (swatch.isSelected()) {
                    applyColor(entry[1]);
                } else {
                    swatch.setSelected(true);
                }
            });
            palette.getChildren().add(swatch);
        }
        customColor.setOnAction(e -> {
            if (!updatingControls && customColor.getValue() != null) {
                paletteGroup.selectToggle(null);
                applyColor(toHex(customColor.getValue()));
            }
        });
        intensitySlider.valueChangingProperty().addListener((obs, was, changing) -> {
            if (!changing) {
                onIntensityChanged();
            }
        });
        intensitySlider.valueProperty().addListener((obs, o, n) -> {
            if (!intensitySlider.isValueChanging()) {
                onIntensityChanged();
            }
        });

        beforeToggle.setToggleGroup(compareGroup);
        afterToggle.setToggleGroup(compareGroup);
        afterToggle.setSelected(true);
        compareGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) {
                compareGroup.selectToggle(o);
            } else {
                showCompareState();
            }
        });

        setPaintControlsEnabled(false);
        paintStatus.setText("Preparando la pintura…");
        saveButton.setDisable(true);
    }

    public void open(GarageVehicle vehicle) {
        this.vehicle = vehicle;
        titleLabel.setText(vehicle.name());
        VehicleMetadata.Paint paint = vehicle.metadata().paint;
        Optional<java.nio.file.Path> editedFile = vehicle.editedTexture();
        currentColor = paint != null && editedFile.isPresent() ? paint.color : null;
        currentIntensity = paint != null ? paint.intensity : DEFAULT_INTENSITY;
        savedPaintKey = paintKey();
        syncControls();

        context.background().execute(() -> {
            try {
                byte[] bytes = Files.readAllBytes(vehicle.originalGlb());
                GlbModel model = GlbModel.load(bytes);
                Image original = new Image(new ByteArrayInputStream(model.textureBytes()));
                if (original.isError()) {
                    throw new UnsupportedModelException("Textura ilegible", original.getException());
                }
                Image edited = null;
                if (currentColor != null) {
                    edited = new Image(editedFile.get().toUri().toString());
                    if (edited.isError() || edited.getWidth() != original.getWidth()
                            || edited.getHeight() != original.getHeight()) {
                        LOG.warning("texture-edited.png ilegible; se vuelve a calcular el color");
                        edited = null;
                    }
                }
                Image editedTexture = edited;
                Platform.runLater(() -> showModel(bytes, model, original, editedTexture));
                preparePaint(model, original);
            } catch (UnsupportedModelException e) {
                LOG.log(Level.WARNING, "UNSUPPORTED_MODEL al abrir " + vehicle.id(), e);
                Platform.runLater(() -> failAndLeave(ApiException.client(ErrorCode.UNSUPPORTED_MODEL, e.getMessage(), e)));
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.SEVERE, "No se pudo abrir " + vehicle.id(), e);
                Platform.runLater(() -> failAndLeave(ApiException.client(ErrorCode.INTERNAL_ERROR, e.toString(), e)));
            }
        });
    }

    private void showModel(byte[] bytes, GlbModel model, Image original, Image edited) {
        glbBytes = bytes;
        originalTexture = original;
        textureWidth = (int) original.getWidth();
        textureHeight = (int) original.getHeight();
        currentTexture = edited != null ? edited : original;
        if (edited != null) {
            editedPixels = FxImages.argb(edited);
        }
        viewer.setModel(model, currentTexture);
        loadingIndicator.setVisible(false);
        viewer.setVisible(true);
        viewerToolbar.setVisible(true);
        saveButton.setDisable(false);
        if (vehicle.thumbnail().isEmpty()) {
            // Miniatura para el garaje tras el primer cuadro dibujado.
            PauseTransition wait = new PauseTransition(Duration.millis(400));
            wait.setOnFinished(e -> saveThumbnailInBackground());
            wait.play();
        }
    }

    /** Calcula la máscara de pintura fuera del hilo de la UI (puede tardar con texturas grandes). */
    private void preparePaint(GlbModel model, Image original) {
        int w = (int) original.getWidth();
        int h = (int) original.getHeight();
        int[] pixels = FxImages.argb(original);
        boolean[] coverage = UvCoverage.compute(model.texCoords(), model.indices(), w, h);
        float[] mask = maskStrategy.computeMask(pixels, w, h, coverage);
        Recolorer prepared = new Recolorer(pixels, mask);
        Platform.runLater(() -> {
            recolorer = prepared;
            setPaintControlsEnabled(true);
            paintStatus.setText(prepared.hasPaint() ? "" : "No se encontró la pintura en la textura.");
            // Si había un color guardado pero la textura editada no se pudo leer, se recalcula.
            if (currentColor != null && editedPixels == null) {
                requestRecolor();
            }
        });
    }

    private void applyColor(String hex) {
        currentColor = hex;
        afterToggle.setSelected(true);
        requestRecolor();
        updateDirty();
    }

    private void onIntensityChanged() {
        if (updatingControls) {
            return;
        }
        currentIntensity = intensitySlider.getValue();
        if (currentColor != null) {
            requestRecolor();
        }
        updateDirty();
    }

    /** Un solo recoloreado a la vez; si llegan cambios mientras corre, se aplica el último. */
    private void requestRecolor() {
        if (recolorer == null || currentColor == null) {
            return;
        }
        if (recoloring) {
            recolorPending = true;
            return;
        }
        recoloring = true;
        paintingIndicator.setVisible(true);
        int target = Integer.parseInt(currentColor.substring(1), 16);
        double intensity = currentIntensity;
        Recolorer r = recolorer;
        context.background().execute(() -> {
            int[] result = r.apply(target, intensity);
            Platform.runLater(() -> {
                recoloring = false;
                if (recolorPending) {
                    recolorPending = false;
                    requestRecolor();
                    return;
                }
                paintingIndicator.setVisible(false);
                if (currentColor == null) {
                    return; // Se restauró el original mientras se calculaba.
                }
                editedPixels = result;
                WritableImage image = FxImages.fromArgb(result, textureWidth, textureHeight);
                currentTexture = image;
                showCompareState();
            });
        });
    }

    @FXML
    private void onRestore() {
        currentColor = null;
        editedPixels = null;
        recolorPending = false;
        currentTexture = originalTexture;
        currentIntensity = DEFAULT_INTENSITY;
        syncControls();
        afterToggle.setSelected(true);
        showCompareState();
        updateDirty();
    }

    private void showCompareState() {
        if (originalTexture == null) {
            return;
        }
        viewer.setTexture(beforeToggle.isSelected() ? originalTexture : currentTexture);
    }

    private void syncControls() {
        updatingControls = true;
        try {
            intensitySlider.setValue(currentIntensity);
            paletteGroup.selectToggle(null);
            if (currentColor != null) {
                paletteGroup.getToggles().stream()
                        .filter(t -> currentColor.equalsIgnoreCase((String) t.getUserData()))
                        .findFirst()
                        .ifPresentOrElse(paletteGroup::selectToggle, () -> customColor.setValue(Color.web(currentColor)));
            }
        } finally {
            updatingControls = false;
        }
    }

    private void setPaintControlsEnabled(boolean enabled) {
        palette.setDisable(!enabled);
        customColor.setDisable(!enabled);
        intensitySlider.setDisable(!enabled);
        restoreButton.setDisable(!enabled);
    }

    private String paintKey() {
        return currentColor == null ? null
                : currentColor.toUpperCase(Locale.ROOT) + "@" + Math.round(currentIntensity * 100);
    }

    private boolean isDirty() {
        return !Objects.equals(savedPaintKey, paintKey());
    }

    private void updateDirty() {
        unsavedLabel.setVisible(isDirty());
    }

    @FXML
    private void onSave() {
        if (saving || originalTexture == null) {
            return;
        }
        if (recoloring) {
            paintStatus.setText("Espera a que termine de aplicar el color.");
            return;
        }
        saving = true;
        saveButton.setDisable(true);
        SaveRequest request = prepareSave();
        context.background().execute(() -> {
            boolean ok = persist(request);
            Platform.runLater(() -> {
                saving = false;
                saveButton.setDisable(false);
                if (ok) {
                    savedPaintKey = request.paintKey();
                    updateDirty();
                    paintStatus.setText("Guardado en tu garaje.");
                } else {
                    Dialogs.info(context.stage(), "Guardar", "No se pudo guardar el vehículo.");
                }
            });
        });
    }

    private record SaveRequest(String color, double intensity, int[] pixels, Image snapshot, String paintKey) {
    }

    /** Toma en el hilo de la UI lo que hace falta para guardar (captura y píxeles actuales). */
    private SaveRequest prepareSave() {
        boolean wasBefore = beforeToggle.isSelected();
        if (wasBefore) {
            viewer.setTexture(currentTexture);
        }
        WritableImage shot = viewer.snapshot();
        if (wasBefore) {
            viewer.setTexture(originalTexture);
        }
        return new SaveRequest(currentColor, currentIntensity, currentColor != null ? editedPixels : null, shot, paintKey());
    }

    private boolean persist(SaveRequest request) {
        try {
            byte[] texture = request.pixels() != null ? FxImages.png(request.pixels(), textureWidth, textureHeight) : null;
            byte[] thumb = FxImages.thumbnailPng(request.snapshot());
            VehicleMetadata.Paint paint = request.color() != null
                    ? new VehicleMetadata.Paint(request.color(), request.intensity()) : null;
            vehicle = context.garage().saveEdits(vehicle, paint, texture, thumb);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.SEVERE, "No se pudo guardar " + vehicle.id(), e);
            return false;
        }
    }

    private void saveThumbnailInBackground() {
        if (originalTexture == null) {
            return;
        }
        WritableImage shot = viewer.snapshot();
        GarageVehicle target = vehicle;
        context.background().execute(() -> {
            try {
                context.garage().saveThumbnail(target, FxImages.thumbnailPng(shot));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "No se pudo guardar la miniatura", e);
            }
        });
    }

    @FXML
    private void onResetView() {
        viewer.resetView();
    }

    @FXML
    private void onExportPng() {
        if (originalTexture == null) {
            return;
        }
        WritableImage shot = viewer.snapshot();
        File target = VehicleExport.chooseTarget(context, vehicle.name(), "Imagen PNG", "*.png");
        if (target == null) {
            return;
        }
        context.background().execute(() -> {
            try {
                Files.write(target.toPath(), FxImages.png(shot));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "No se pudo exportar la imagen", e);
                Platform.runLater(() -> Dialogs.info(context.stage(), "Exportar", "No se pudo exportar la imagen."));
            }
        });
    }

    @FXML
    private void onExportGlb() {
        if (glbBytes == null) {
            return;
        }
        if (recoloring) {
            paintStatus.setText("Espera a que termine de aplicar el color.");
            return;
        }
        int[] pixels = currentColor != null ? editedPixels : null;
        File target = VehicleExport.chooseTarget(context, vehicle.name(), "Modelo GLB", "*.glb");
        if (target == null) {
            return;
        }
        byte[] glb = glbBytes;
        context.background().execute(() -> {
            try {
                byte[] out = pixels != null
                        ? VehicleExport.withTexture(glb, FxImages.png(pixels, textureWidth, textureHeight))
                        : glb;
                Files.write(target.toPath(), out);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "No se pudo exportar el modelo", e);
                Platform.runLater(() -> Dialogs.info(context.stage(), "Exportar", "No se pudo exportar el modelo."));
            }
        });
    }

    @FXML
    private void onBack() {
        context.navigator().goTo(Navigator.Screen.GARAGE);
    }

    private void failAndLeave(ApiException error) {
        ErrorDialog.show(context.stage(), error);
        savedPaintKey = paintKey();
        context.navigator().goTo(Navigator.Screen.GARAGE);
    }

    @Override
    public boolean canLeave() {
        if (!isDirty() || originalTexture == null) {
            return true;
        }
        Optional<ButtonBar.ButtonData> choice = Dialogs.unsavedChanges(context.stage());
        if (choice.isEmpty()) {
            return false;
        }
        if (choice.get() == ButtonBar.ButtonData.YES) {
            if (recoloring) {
                return false;
            }
            return persist(prepareSave());
        }
        return true;
    }

    @Override
    public void onLeave() {
        viewer.dispose();
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }
}
