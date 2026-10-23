package com.tunai.desktop;

import com.tunai.desktop.api.ApiConfig;
import com.tunai.desktop.api.TunaiApiClient;
import com.tunai.desktop.classifier.OnnxVehicleClassifier;
import com.tunai.desktop.config.AppPaths;
import com.tunai.desktop.config.InstallationId;
import com.tunai.desktop.config.SettingsStore;
import com.tunai.desktop.garage.GarageRepository;
import com.tunai.desktop.ui.Credits;
import com.tunai.desktop.ui.Navigator;
import com.tunai.desktop.ui.ServerMonitor;
import com.tunai.desktop.ui.SessionState;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Servicios compartidos por todas las pantallas. */
public class AppContext {

    private final AppPaths paths;
    private final SettingsStore settings;
    private final InstallationId installationId;
    private final GarageRepository garage;
    private final SessionState session = new SessionState();
    private final TunaiApiClient api;
    private final ServerMonitor serverMonitor;
    private final OnnxVehicleClassifier classifier;
    /** Tareas en segundo plano (clasificar, generar, recolorear) para no congelar la ventana. */
    private final ExecutorService background = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tunai-background");
        t.setDaemon(true);
        return t;
    });
    private Navigator navigator;
    private Stage stage;

    public AppContext(AppPaths paths) {
        this.paths = paths;
        this.settings = new SettingsStore(paths.settingsFile());
        this.installationId = InstallationId.loadOrCreate(paths.installationIdFile());
        this.garage = new GarageRepository(paths.garageDir());
        this.api = new TunaiApiClient(ApiConfig.resolveBaseUrl(settings.get()), installationId::value, App.version());
        this.serverMonitor = new ServerMonitor(api, session, installationId);
        this.classifier = new OnnxVehicleClassifier(() -> settings.get().classifier.threshold,
                paths.root().resolve("vehicle-classes.txt"));
        Integer credits = settings.get().credits;
        session.creditsProperty().set(credits != null ? credits : Credits.INITIAL);
        session.quotaProperty().addListener((obs, old, quota) -> {
            if (quota != null && quota.creditsPerModel() != null) {
                session.creditsPerModelProperty().set(quota.creditsPerModel());
            }
            if (quota != null && quota.credits() != null) {
                syncCredits(quota.credits());
            }
        });
    }

    /**
     * Aplica el saldo que informa el backend. Se queda con el menor de los dos: el backend guarda la cuota en memoria
     * y, si Render se reinicia, volvería a dar los créditos iniciales. Solo en el hilo de JavaFX.
     */
    public void syncCredits(int backendCredits) {
        int credits = Math.max(0, Math.min(session.getCredits(), backendCredits));
        if (credits != session.getCredits() || settings.get().credits == null) {
            session.creditsProperty().set(credits);
            settings.get().credits = credits;
            settings.save();
        }
    }

    /** Descuenta un modelo 3D de los créditos y lo guarda. Solo en el hilo de JavaFX. */
    public void spendModelCredits() {
        syncCredits(session.getCredits() - session.getCreditsPerModel());
    }

    void attach(Stage stage, Navigator navigator) {
        this.stage = stage;
        this.navigator = navigator;
    }

    public AppPaths paths() {
        return paths;
    }

    public SettingsStore settings() {
        return settings;
    }

    public InstallationId installationId() {
        return installationId;
    }

    public GarageRepository garage() {
        return garage;
    }

    public TunaiApiClient api() {
        return api;
    }

    public ServerMonitor serverMonitor() {
        return serverMonitor;
    }

    public OnnxVehicleClassifier classifier() {
        return classifier;
    }

    public ExecutorService background() {
        return background;
    }

    public SessionState session() {
        return session;
    }

    public Navigator navigator() {
        return navigator;
    }

    public Stage stage() {
        return stage;
    }
}
