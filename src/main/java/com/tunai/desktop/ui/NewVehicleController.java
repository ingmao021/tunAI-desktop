package com.tunai.desktop.ui;

import com.tunai.desktop.AppContext;
import com.tunai.desktop.api.ErrorCode;
import com.tunai.desktop.api.Quota;
import com.tunai.desktop.classifier.VehicleClassifier;
import com.tunai.desktop.image.Photo;
import com.tunai.desktop.image.PhotoDecoder;
import com.tunai.desktop.image.PhotoValidator;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Nuevo vehículo (§5.3): elegir foto, validación local y verificación de vehículo (§5.3.1). */
public class NewVehicleController implements NavigationAware {

    private static final Logger LOG = Logger.getLogger(NewVehicleController.class.getName());
    private static final double PREVIEW_MARGIN = 24;

    private final AppContext context;
    private final ChangeListener<Quota> quotaListener = (obs, o, n) -> updateQuota();

    @FXML
    private StackPane dropZone;
    @FXML
    private ImageView previewImage;
    @FXML
    private VBox dropHint;
    @FXML
    private ProgressIndicator checkingIndicator;
    @FXML
    private Label statusLabel;
    @FXML
    private Button changePhotoButton;
    @FXML
    private TextField nameField;
    @FXML
    private Label quotaWarning;
    @FXML
    private Button generateButton;

    private Photo photo;
    private Image preview;
    private boolean vehicleConfirmed;
    /** Aumenta con cada foto elegida; los resultados de una foto anterior se descartan. */
    private long selection;

    public NewVehicleController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        previewImage.fitWidthProperty().bind(dropZone.widthProperty().subtract(PREVIEW_MARGIN));
        previewImage.fitHeightProperty().bind(dropZone.heightProperty().subtract(PREVIEW_MARGIN));
        dropZone.setOnDragOver(this::onDragOver);
        dropZone.setOnDragExited(e -> dropZone.getStyleClass().remove("drag-over"));
        dropZone.setOnDragDropped(this::onDragDropped);
        nameField.textProperty().addListener((obs, o, n) -> updateGenerateButton());
        context.session().quotaProperty().addListener(quotaListener);
        updateQuota();
        setStatus(null, null);
    }

    /** Vuelve a esta pantalla tras un error con una foto ya elegida (por ejemplo {@code SERVICE_BUSY}). */
    public void restore(Photo photo, String name) {
        nameField.setText(name);
        loadPhoto(photo, null);
    }

    private void updateQuota() {
        quotaWarning.setText(QuotaText.generationWarning(context.session().getQuota()));
        updateGenerateButton();
    }

    private void updateGenerateButton() {
        Quota quota = context.session().getQuota();
        boolean quotaLeft = quota == null || !quota.exhausted();
        generateButton.setDisable(photo == null || !vehicleConfirmed || nameField.getText().isBlank() || !quotaLeft
                || !context.settings().get().hasConsent());
    }

    private void onDragOver(DragEvent e) {
        if (e.getDragboard().hasFiles() && e.getDragboard().getFiles().size() == 1) {
            e.acceptTransferModes(TransferMode.COPY);
            if (!dropZone.getStyleClass().contains("drag-over")) {
                dropZone.getStyleClass().add("drag-over");
            }
        }
        e.consume();
    }

    private void onDragDropped(DragEvent e) {
        List<File> files = e.getDragboard().getFiles();
        boolean ok = files.size() == 1;
        if (ok) {
            selectFile(files.get(0).toPath());
        }
        e.setDropCompleted(ok);
        e.consume();
    }

    @FXML
    private void onChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Elegir foto del carro");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Fotos JPG o PNG", "*.jpg", "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        String last = context.settings().get().lastFolder;
        if (last != null && Files.isDirectory(Path.of(last))) {
            chooser.setInitialDirectory(new File(last));
        }
        File file = chooser.showOpenDialog(context.stage());
        if (file != null) {
            if (file.getParent() != null) {
                context.settings().get().lastFolder = file.getParent();
                context.settings().save();
            }
            selectFile(file.toPath());
        }
    }

    void selectFile(Path file) {
        long current = ++selection;
        resetPhoto();
        setStatus("◌ Revisando la foto…", null);
        checking(true);
        context.background().execute(() -> {
            try {
                Photo validated = PhotoValidator.validate(file);
                Platform.runLater(() -> {
                    if (current == selection) {
                        loadPhoto(validated, current);
                    }
                });
            } catch (PhotoValidator.InvalidPhotoException e) {
                Platform.runLater(() -> {
                    if (current == selection) {
                        checking(false);
                        setStatus("✗ " + e.getMessage(), "error-text");
                    }
                });
            }
        });
    }

    /** Decodifica, muestra la vista previa y corre el clasificador, todo fuera del hilo de la UI. */
    private void loadPhoto(Photo validated, Long token) {
        long current = token != null ? token : ++selection;
        resetPhoto();
        setStatus("◌ Revisando la foto…", null);
        checking(true);
        context.background().execute(() -> {
            BufferedImage image;
            try {
                image = PhotoDecoder.decode(validated);
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (current == selection) {
                        checking(false);
                        setStatus("✗ " + PhotoValidator.ERR_UNREADABLE, "error-text");
                    }
                });
                return;
            }
            Image fxPreview = SwingFXUtils.toFXImage(image, null);
            Platform.runLater(() -> {
                if (current == selection) {
                    showPreview(validated, fxPreview);
                }
            });
            CheckOutcome outcome = classify(image);
            Platform.runLater(() -> {
                if (current == selection) {
                    checking(false);
                    applyOutcome(outcome);
                }
            });
        });
    }

    private record CheckOutcome(boolean vehicle, String message, String style) {
    }

    private CheckOutcome classify(BufferedImage image) {
        if (!context.settings().get().classifier.enabled) {
            LOG.warning("Clasificador desactivado en settings.json (classifier.enabled=false)");
            return new CheckOutcome(true, "✓ Foto lista", "ok-text");
        }
        try {
            VehicleClassifier.Result result = context.classifier().classify(image);
            LOG.info(String.format(Locale.ROOT, "Clasificación: vehículo=%s suma=%.3f top5=%s",
                    result.isVehicle(), result.vehicleScore(), result.top5()));
            if (result.isVehicle()) {
                long percent = Math.round(Math.min(1, result.vehicleScore()) * 100);
                return new CheckOutcome(true, "✓ Vehículo detectado (" + percent + " %)", "ok-text");
            }
            return new CheckOutcome(false, "✗ " + ErrorCode.NOT_A_VEHICLE.message(), "error-text");
        } catch (VehicleClassifier.ClassifierUnavailableException e) {
            LOG.log(Level.SEVERE, "CLASSIFIER_UNAVAILABLE", e);
            return new CheckOutcome(false, "✗ " + ErrorCode.CLASSIFIER_UNAVAILABLE.message(), "error-text");
        }
    }

    private void showPreview(Photo validated, Image fxPreview) {
        photo = validated;
        preview = fxPreview;
        previewImage.setImage(fxPreview);
        dropHint.setVisible(false);
        changePhotoButton.setVisible(true);
        changePhotoButton.setManaged(true);
    }

    private void applyOutcome(CheckOutcome outcome) {
        vehicleConfirmed = outcome.vehicle();
        setStatus(outcome.message(), outcome.style());
        updateGenerateButton();
    }

    private void resetPhoto() {
        photo = null;
        preview = null;
        vehicleConfirmed = false;
        previewImage.setImage(null);
        dropHint.setVisible(true);
        changePhotoButton.setVisible(false);
        changePhotoButton.setManaged(false);
        updateGenerateButton();
    }

    private void checking(boolean on) {
        checkingIndicator.setVisible(on);
        checkingIndicator.setManaged(on);
    }

    private void setStatus(String text, String styleClass) {
        statusLabel.getStyleClass().removeAll("ok-text", "error-text");
        statusLabel.setText(text == null ? "" : text);
        if (styleClass != null) {
            statusLabel.getStyleClass().add(styleClass);
        }
    }

    @FXML
    private void onGenerate() {
        if (generateButton.isDisabled() || photo == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(context.stage(), "Generar modelo",
                "Vas a usar 1 de tus generaciones. ¿Continuar?", "Generar");
        if (!confirmed) {
            return;
        }
        Photo selected = photo;
        Image selectedPreview = preview;
        String name = nameField.getText().strip();
        context.navigator().<GeneratingController>goTo(Navigator.Screen.GENERATING,
                c -> c.start(selected, selectedPreview, name));
    }

    @FXML
    private void onCancel() {
        context.navigator().goTo(Navigator.Screen.GARAGE);
    }

    @Override
    public void onLeave() {
        selection++;
        context.session().quotaProperty().removeListener(quotaListener);
    }
}
