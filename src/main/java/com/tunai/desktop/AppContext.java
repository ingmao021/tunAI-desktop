package com.tunai.desktop;

import com.tunai.desktop.config.AppPaths;
import com.tunai.desktop.config.InstallationId;
import com.tunai.desktop.config.SettingsStore;
import com.tunai.desktop.garage.GarageRepository;
import com.tunai.desktop.ui.Navigator;
import com.tunai.desktop.ui.SessionState;
import javafx.stage.Stage;

/** Servicios compartidos por todas las pantallas. */
public class AppContext {

    private final AppPaths paths;
    private final SettingsStore settings;
    private final InstallationId installationId;
    private final GarageRepository garage;
    private final SessionState session = new SessionState();
    private Navigator navigator;
    private Stage stage;

    public AppContext(AppPaths paths) {
        this.paths = paths;
        this.settings = new SettingsStore(paths.settingsFile());
        this.installationId = InstallationId.loadOrCreate(paths.installationIdFile());
        this.garage = new GarageRepository(paths.garageDir());
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
