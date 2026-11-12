package com.tunai.desktop.ui;

import com.tunai.desktop.App;
import org.testfx.api.FxToolkit;

import java.nio.file.Files;
import java.nio.file.Path;

/** Arranque de la app real para pruebas de UI: carpeta temporal, sesión válida y servidor inexistente. */
final class UiTestApp {

    private UiTestApp() {
    }

    static App start(Path home, boolean consent) throws Exception {
        System.setProperty("tunai.home", home.toString());
        System.setProperty("tunai.api.url", "http://127.0.0.1:9");
        String consentJson = consent ? "\"consentAcceptedAt\":\"2026-01-01T00:00:00Z\"," : "";
        Files.writeString(home.resolve("settings.json"),
                "{\"account\":\"demo\"," + consentJson + "\"session\":{\"username\":\"demo\",\"token\":\"t\","
                        + "\"expiresAt\":\"2099-01-01T00:00:00Z\"}}");
        FxToolkit.registerPrimaryStage();
        return (App) FxToolkit.setupApplication(App.class);
    }
}
