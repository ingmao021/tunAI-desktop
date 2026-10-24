package com.tunai.desktop.ui;

import com.tunai.desktop.App;
import javafx.application.Application;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

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
        // Primera ventana del proceso: si otra (la terminal) tiene el foco, Windows puede gastar uno o más clics en
        // activarla; depende del escritorio, no de la app. Se repite el clic hasta 3 veces y se exige el resultado.
        CheckBox accept = lookup("#acceptCheck").queryAs(CheckBox.class);
        for (int attempt = 0; attempt < 3 && !accept.isSelected(); attempt++) {
            clickOn(accept);
            WaitForAsyncUtils.waitForFxEvents();
        }
        assertTrue(accept.isSelected());
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
