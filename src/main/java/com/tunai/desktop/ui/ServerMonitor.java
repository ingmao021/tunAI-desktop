package com.tunai.desktop.ui;

import com.tunai.desktop.api.ApiException;
import com.tunai.desktop.api.ErrorCode;
import com.tunai.desktop.api.Quota;
import com.tunai.desktop.api.TunaiApiClient;
import com.tunai.desktop.config.InstallationId;
import javafx.application.Platform;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Indicador del servidor y contador de cuota (§5.2). Llama a {@code /actuator/health} en cuanto
 * abre la app (calentamiento de Render) y reintenta cada 15 s mientras no haya conexión.
 */
public class ServerMonitor {

    private static final Logger LOG = Logger.getLogger(ServerMonitor.class.getName());
    static final long RETRY_SECONDS = 15;
    static final long READY_RECHECK_SECONDS = 60;

    private final TunaiApiClient api;
    private final SessionState session;
    private final InstallationId installationId;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "server-monitor");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> next;
    private volatile Boolean lastUp;

    public ServerMonitor(TunaiApiClient api, SessionState session, InstallationId installationId) {
        this.api = api;
        this.session = session;
        this.installationId = installationId;
    }

    public void start() {
        schedule(0);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    /** Pide la cuota otra vez (por ejemplo al volver al garaje). */
    public void refreshQuota() {
        scheduler.execute(this::fetchQuota);
    }

    /** Llamado desde fuera cuando una petición confirma que el servidor responde. */
    public void markReady() {
        Platform.runLater(() -> session.serverStatusProperty().set(SessionState.ServerStatus.READY));
    }

    public void updateQuota(Quota quota) {
        if (quota != null) {
            Platform.runLater(() -> session.quotaProperty().set(quota));
        }
    }

    private synchronized void schedule(long delaySeconds) {
        if (scheduler.isShutdown()) {
            return;
        }
        if (next != null) {
            next.cancel(false);
        }
        next = scheduler.schedule(this::check, delaySeconds, TimeUnit.SECONDS);
    }

    private void check() {
        boolean up = api.health();
        if (lastUp == null || lastUp != up) {
            LOG.info("Servidor " + (up ? "listo" : "sin conexión") + ": " + api.baseUrl());
            lastUp = up;
        }
        SessionState.ServerStatus status = up ? SessionState.ServerStatus.READY : SessionState.ServerStatus.OFFLINE;
        Platform.runLater(() -> session.serverStatusProperty().set(status));
        if (up) {
            fetchQuota();
        }
        schedule(up ? READY_RECHECK_SECONDS : RETRY_SECONDS);
    }

    private void fetchQuota() {
        try {
            updateQuota(api.quota());
        } catch (ApiException e) {
            if (e.code() == ErrorCode.INVALID_INSTALLATION_ID) {
                installationId.regenerate();
            } else {
                LOG.info("No se pudo leer la cuota: " + e.getMessage());
            }
        }
    }
}
