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
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generando (§5.4). La barra es indeterminada y va con un cronómetro. Una generación que falla
 * <b>nunca</b> se reintenta sola: el usuario decide. La única excepción es el timeout que el servidor declara
 * retomable ({@link ApiException#resumable()}): el modelo sigue en marcha y repetir la petición con la misma foto
 * lo recoge sin crear ni cobrar otro, así que se espera y se retoma sin mostrar un error falso.
 */
public class GeneratingController implements NavigationAware {

    private static final Logger LOG = Logger.getLogger(GeneratingController.class.getName());

    /** Retomas seguidas antes de rendirse; cada una espera hasta ~85 s en el servidor, que conserva la tarea 10 min. */
    static final int MAX_RESUMES = 8;
    /** La retoma ya es una espera larga en el servidor: la pausa entre retomas solo retrasa la entrega. */
    private static final int MIN_RESUME_WAIT_SECONDS = 2;
    private static final int MAX_RESUME_WAIT_SECONDS = 60;
    private static final int DEFAULT_RESUME_WAIT_SECONDS = 5;

    private static final String GENERATE_LABEL = "Generando modelo 3D";

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
        setStep(stepGenerate, GENERATE_LABEL, StepState.PENDING);
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
            GenerationResult result = generateResuming(myCancel);
            context.serverMonitor().markReady();
            // El backend ya entregó el modelo: se cobra aunque luego no se pueda abrir en el visor.
            Platform.runLater(context::spendModelCredits);
            if (result.quota() != null) {
                context.serverMonitor().updateQuota(result.quota());
            } else {
                context.serverMonitor().refreshQuota();
            }

            Platform.runLater(() -> {
                setStep(stepGenerate, GENERATE_LABEL, StepState.DONE);
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

    /**
     * Pide el modelo y, si el servidor dice que sigue generándolo ({@code resumable}), espera su {@code Retry-After}
     * y repite con la <b>misma foto</b>: el servidor se une a la tarea en curso. Cualquier otro error se propaga.
     *
     * <p>Solo la primera petición, la que el usuario confirmó, puede empezar una generación. Las retomas van
     * marcadas: si el servidor ya no tiene la generación (se reinició), responde un error en vez de crear otra.
     */
    private GenerationResult generateResuming(CompletableFuture<Void> myCancel) throws ApiException {
        for (int resumes = 0; ; resumes++) {
            try {
                return context.api().generate(photo.bytes(), photo.fileName(), photo.format().mimeType(),
                        resumes > 0, myCancel);
            } catch (ApiException e) {
                if (!e.resumable() || resumes >= MAX_RESUMES || myCancel.isDone()) {
                    throw e;
                }
                int wait = resumeWaitSeconds(e.retryAfterSeconds());
                LOG.info("El modelo sigue generándose en el servidor; se retoma en " + wait + " s (retoma "
                        + (resumes + 1) + "/" + MAX_RESUMES + ") traceId=" + e.traceId());
                Platform.runLater(() -> setStep(stepGenerate,
                        GENERATE_LABEL + " (sigue en el servidor, retomando…)", StepState.ACTIVE));
                awaitOrCancel(myCancel, wait);
            }
        }
    }

    static int resumeWaitSeconds(Integer retryAfter) {
        int seconds = retryAfter != null ? retryAfter : DEFAULT_RESUME_WAIT_SECONDS;
        return Math.max(MIN_RESUME_WAIT_SECONDS, Math.min(MAX_RESUME_WAIT_SECONDS, seconds));
    }

    /** Duerme {@code seconds}; si el usuario deja de esperar antes, termina como cancelado. */
    private static void awaitOrCancel(CompletableFuture<Void> myCancel, int seconds) throws ApiException {
        try {
            myCancel.get(seconds, java.util.concurrent.TimeUnit.SECONDS);
            throw new TunaiApiClient.CancelledException();
        } catch (java.util.concurrent.TimeoutException e) {
            // Pasó la espera sin cancelar: se retoma
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TunaiApiClient.CancelledException();
        } catch (java.util.concurrent.ExecutionException e) {
            throw new TunaiApiClient.CancelledException();
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
        if (error.code().action() == ErrorCode.Action.SHOW_PLANS) {
            context.syncCredits(0);
            goToGarage();
            PricingDialog.show(context.stage(), context.session().getCreditsPerModel());
            return;
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
            case SIGN_OUT -> context.signOut();
            case STAY, BACK_TO_GARAGE, SHOW_PLANS -> goToGarage();
        }
    }

    /** CA-UI-05: cada generación, incluido un reintento manual, pide confirmación. */
    private boolean confirmRetry() {
        return Dialogs.confirm(context.stage(), "Reintentar",
                Credits.confirmMessage(context.session().getCreditsPerModel()), "Generar");
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
                "La generación sigue en curso y podría contarse igual. Si vuelves a generar con la misma foto, "
                        + "se retoma sin gastar otro modelo. ¿Dejar de esperar?",
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
        label.getStyleClass().removeAll("step-pending", "step-active", "step-done");
        label.setText(base);
        switch (state) {
            case PENDING -> {
                label.setGraphic(new FontIcon("mdmz-radio_button_unchecked"));
                label.getStyleClass().add("step-pending");
            }
            case ACTIVE -> {
                label.setGraphic(new FontIcon("mdal-autorenew"));
                label.getStyleClass().add("step-active");
            }
            case DONE -> {
                label.setGraphic(new FontIcon("mdal-check_circle"));
                label.getStyleClass().add("step-done");
            }
        }
    }
}
