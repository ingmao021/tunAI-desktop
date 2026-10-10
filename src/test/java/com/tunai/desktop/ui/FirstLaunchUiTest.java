package com.tunai.desktop.ui;

import com.tunai.desktop.App;
import javafx.application.Application;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CA-UI-01 y CA-UI-02: consentimiento solo la primera vez; garaje sin servidor. */
class FirstLaunchUiTest extends FxRobot {

    @TempDir
    Path home;

    private Application app;

    @AfterEach
    void tearDown() throws Exception {
        if (app != null) {
            FxToolkit.cleanupApplication(app);
        }
        FxToolkit.hideStage();
    }

    @Test
    void consentShownOnlyUntilAccepted() throws Exception {
        System.setProperty("tunai.home", home.toString());
        // Servidor inexistente: el garaje debe funcionar igual.
        System.setProperty("tunai.api.url", "http://127.0.0.1:9");

        FxToolkit.registerPrimaryStage();
        app = FxToolkit.setupApplication(App.class);

        Button start = lookup("Empezar").queryButton();
        assertTrue(start.isDisabled());
        clickOn("Acepto el envío de mis fotos para generar modelos");
        assertFalse(start.isDisabled());
        clickOn(start);

        Label empty = lookup("Aún no tienes vehículos. Crea el primero con + Nuevo vehículo").queryAs(Label.class);
        assertTrue(empty.isVisible());

        FxToolkit.cleanupApplication(app);
        app = FxToolkit.setupApplication(App.class);
        assertTrue(lookup("Mi garaje").tryQuery().isPresent());
        assertTrue(lookup("Empezar").tryQuery().isEmpty());
    }
}
