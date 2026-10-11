package com.tunai.desktop.ui;

import com.tunai.desktop.AppContext;
import com.tunai.desktop.api.ApiException;
import com.tunai.desktop.api.ErrorCode;
import com.tunai.desktop.api.GenerationResult;
import com.tunai.desktop.api.Quota;
import com.tunai.desktop.api.TunaiApiClient;
import com.tunai.desktop.garage.GarageVehicle;
import com.tunai.desktop.image.Photo;
import com.tunai.desktop.viewer.GlbModel;
import com.tunai.desktop.viewer.UnsupportedModelException;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generando (§5.4). La barra es indeterminada y va con un cronómetro. La generación
 * <b>nunca</b> se reintenta sola: si falla, el usuario decide.
 */
public class GeneratingController implements NavigationAware {

    private static final Logger LOG = Logger.getLogger(GeneratingController.class.getName());

    private enum StepState { PENDING, ACTIVE, DONE }

    private final AppContext context;

    @FXML
    private ImageView photoThumb;
    @FXML
    private Label stepValidated;
    @FXML
    private Label stepWake;
    @FXML
    private Label stepGenerate;
    @FXML
    private Label stepPrepare;
    @FXML
    private ProgressBar progress;
    @FXML
    private Label elapsedLabel;
    @FXML
    private Button cancelButton;

    private Photo photo;
    private String name;
    private Timeline clock;
    private CompletableFuture<Void> cancel;
    private volatile boolean running;

    public GeneratingController(AppContext context) {
        this.context = context;
    }

    public void start(Photo photo, Image preview, String name) {
        this.photo = photo;
        this.name = name;
        photoThumb.setImage(preview);
        run();
    }

    private void run() {
        boolean serverReady = context.session().getServerStatus() == SessionState.ServerStatus.READY;
        setStep(stepValidated, "Foto validada", StepState.DONE);
        stepWake.setVisible(!serverReady);
        stepWake.setManaged(!serverReady);
        setStep(stepWake, "Calentando motores del taller 3D…  (puede tardar ~1 min)", StepState.PENDING);
        setStep(stepGenerate, "Generando modelo 3D", StepState.PENDING);
        setStep(stepPrepare, "Preparando visor", StepState.PENDING);
        cancelButton.setDisable(false);
        startClock();

        cancel = new CompletableFuture<>();
        running = true;
        CompletableFuture<Void> myCancel = cancel;
        context.background().execute(() -> work(serverReady, myCancel));
    }

    private void work(boolean serverReady, CompletableFuture<Void> myCancel) {
        try {
            if (!serverReady) {
                Platform.runLater(() -> setStep(stepWake, null, StepState.ACTIVE));
                if (!context.api().health()) {
                    throw ApiException.client(ErrorCode.NETWORK_ERROR, "El servidor no despertó", null);
                }
                context.serverMonitor().markReady();
                Platform.runLater(() -> setStep(stepWake, null, StepState.DONE));
            }
            if (myCancel.isDone()) {
                return;
            }
            Platform.runLater(() -> setStep(stepGenerate, null, StepState.ACTIVE));
            GenerationResult result = context.api().generate(photo.bytes(), photo.fileName(),
                    photo.format().mimeType(), myCancel);
            context.serverMonitor().markReady();
            if (result.quota() != null) {
                context.serverMonitor().updateQuota(result.quota());
            } else {
                context.serverMonitor().refreshQuota();
            }

            Platform.runLater(() -> {
                setStep(stepGenerate, null, StepState.DONE);
                setStep(stepPrepare, null, StepState.ACTIVE);
            });
            GarageVehicle vehicle = prepare(result.glb());
            Platform.runLater(() -> finishSuccess(vehicle));
        } catch (TunaiApiClient.CancelledException e) {
            LOG.info("El usuario dejó de esperar la generación");
        } catch (ApiException e) {
            Platform.runLater(() -> finishError(e));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Error inesperado al generar", e);
            Platform.runLater(() -> finishError(ApiException.client(ErrorCode.INTERNAL_ERROR, e.toString(), e)));
        }
    }

    /** Valida el GLB (§3) y lo guarda en el garaje antes de abrir el visor. */
    private GarageVehicle prepare(byte[] glb) throws ApiException {
        try {
            GlbModel.validate(glb);
        } catch (UnsupportedModelException e) {
            Path kept = keepRejectedModel(glb);
            LOG.warning("UNSUPPORTED_MODEL: " + e.getMessage() + (kept != null ? " (guardado en " + kept + ")" : ""));
            throw ApiException.client(ErrorCode.UNSUPPORTED_MODEL, e.getMessage(), e);
        }
        try {
            return context.garage().create(name, glb);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "No se pudo guardar el modelo en el garaje", e);
            throw ApiException.client(ErrorCode.INTERNAL_ERROR, "No se pudo guardar el modelo", e);
        }
    }

    /** Cada generación cuesta cuota: un modelo no compatible se conserva para poder ajustar el visor. */
    private Path keepRejectedModel(byte[] glb) {
        try {
            Path dir = Files.createDirectories(context.paths().logsDir());
            Path file = dir.resolve("unsupported-" + Instant.now().toEpochMilli() + ".glb");
            Files.write(file, glb);
            return file;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo conservar el modelo no compatible", e);
            return null;
        }
    }

    private void finishSuccess(GarageVehicle vehicle) {
        if (!running) {
            return;
        }
        running = false;
        stopClock();
        setStep(stepPrepare, null, StepState.DONE);
        context.navigator().<ViewerController>goTo(Navigator.Screen.VIEWER, c -> c.open(vehicle));
    }

    private void finishError(ApiException error) {
        if (!running) {
            return;
        }
        running = false;
        stopClock();
        progress.setProgress(0);
        cancelButton.setDisable(true);
        if (error.code().logged()) {
            LOG.log(Level.WARNING, "Error en la generación: " + error.getMessage() + " traceId=" + error.traceId(), error);
        }
        if (error.quota() != null) {
            context.serverMonitor().updateQuota(error.quota());
        }

        ErrorDialog.Choice choice = ErrorDialog.show(context.stage(), error);
        switch (error.code().action()) {
            case RETRY, RETRY_COUNTDOWN -> {
                if (choice == ErrorDialog.Choice.RETRY && confirmRetry()) {
                    run();
                } else {
                    goToGarage();
                }
            }
            case BACK_TO_NEW_VEHICLE -> context.navigator().goTo(Navigator.Screen.NEW_VEHICLE);
            case QUOTA_EXHAUSTED -> {
                Quota current = context.session().getQuota();
                int limit = current != null ? current.limit() : 0;
                context.serverMonitor().updateQuota(new Quota(limit, 0, current != null ? current.resetsAt() : null));
                goToGarage();
            }
            case REGENERATE_ID -> {
                context.installationId().regenerate();
                goToGarage();
            }
            case STAY, BACK_TO_GARAGE -> goToGarage();
        }
    }

    /** CA-UI-05: cada generación, incluido un reintento manual, pide confirmación. */
    private boolean confirmRetry() {
        return Dialogs.confirm(context.stage(), "Reintentar",
                "Vas a usar 1 de tus generaciones. ¿Continuar?", "Generar");
    }

    private void goToGarage() {
        context.navigator().goTo(Navigator.Screen.GARAGE);
    }

    @FXML
    private void onCancel() {
        stopWaiting();
        context.serverMonitor().refreshQuota();
        goToGarage();
    }

    private void stopWaiting() {
        running = false;
        stopClock();
        if (cancel != null) {
            cancel.complete(null);
        }
    }

    @Override
    public boolean canLeave() {
        if (!running) {
            return true;
        }
        boolean leave = Dialogs.confirm(context.stage(), "Dejar de esperar",
                "La generación sigue en curso. Si dejas de esperar, podría contarse igual. ¿Dejar de esperar?",
                "Dejar de esperar");
        if (leave) {
            stopWaiting();
        }
        return leave;
    }

    @Override
    public void onLeave() {
        stopWaiting();
    }

    private void startClock() {
        stopClock();
        long startedAt = System.nanoTime();
        elapsedLabel.setText("0:00");
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long seconds = (System.nanoTime() - startedAt) / 1_000_000_000L;
            elapsedLabel.setText(String.format("%d:%02d", seconds / 60, seconds % 60));
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    private void stopClock() {
        if (clock != null) {
            clock.stop();
        }
    }

    private static void setStep(Label label, String text, StepState state) {
        String base = text != null ? text : (String) label.getUserData();
        label.setUserData(base);
        label.getStyleClass().removeAll("step-pending", "step-done");
        switch (state) {
            case PENDING -> {
                label.setText("○  " + base);
                label.getStyleClass().add("step-pending");
                label.setStyle("");
            }
            case ACTIVE -> {
                label.setText("◌  " + base);
                label.setStyle("-fx-font-weight: bold;");
            }
            case DONE -> {
                label.setText("✓  " + base);
                label.getStyleClass().add("step-done");
                label.setStyle("");
            }
        }
    }
}
