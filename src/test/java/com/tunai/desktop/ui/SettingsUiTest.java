package com.tunai.desktop.ui;

import atlantafx.base.controls.ToggleSwitch;
import com.tunai.desktop.App;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pantalla de ajustes: cuenta, créditos, privacidad y cierre de sesión. */
class SettingsUiTest extends FxRobot {

    @TempDir
    Path home;

    private App app;

    @BeforeEach
    void setUp() throws Exception {
        app = UiTestApp.start(home, true);
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.asyncFx(() -> app.context().navigator().goTo(Navigator.Screen.SETTINGS))
                .get(5, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> app.context().navigator().currentController() instanceof SettingsController);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterEach
    void tearDown() throws Exception {
        FxToolkit.cleanupApplication(app);
    }

    private void fx(Runnable r) throws Exception {
        WaitForAsyncUtils.asyncFx(r).get(5, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void showsTheAccountAndTheCreditBalance() {
        assertTrue(lookup("demo").tryQuery().isPresent(), "Nombre de la cuenta");
        Label balance = lookup(".kpi-value").queryAllAs(Label.class).iterator().next();
        assertEquals(String.valueOf(app.context().session().getCredits()), balance.getText());
        assertTrue(lookup("Ver planes").tryQuery().isPresent());
        assertTrue(lookup("Versión " + App.version()).tryQuery().isPresent());
    }

    @Test
    void privacySwitchReflectsAndUpdatesTheConsent() throws Exception {
        ToggleSwitch consent = lookup(n -> n instanceof ToggleSwitch).queryAs(ToggleSwitch.class);
        assertTrue(consent.isSelected());

        fx(() -> consent.setSelected(false));
        assertNull(app.context().settings().get().consentAcceptedAt);
        assertFalse(app.context().settings().get().hasConsent());

        fx(() -> consent.setSelected(true));
        assertNotNull(app.context().settings().get().consentAcceptedAt);
    }

    @Test
    void consentChoiceIsSavedToDisk() throws Exception {
        ToggleSwitch consent = lookup(n -> n instanceof ToggleSwitch).queryAs(ToggleSwitch.class);
        fx(() -> consent.setSelected(false));
        String json = java.nio.file.Files.readString(home.resolve("settings.json"));
        assertFalse(json.contains("consentAcceptedAt\":\"20"), "El consentimiento revocado no queda guardado: " + json);
    }

    @Test
    void signOutButtonReturnsToTheLogin() throws Exception {
        Button signOut = lookup(".settings-card .button").queryAllAs(Button.class).stream()
                .filter(b -> "Cerrar sesión".equals(b.getText())).findFirst().orElseThrow();
        fx(signOut::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> app.context().navigator().currentController() instanceof LoginController);
        assertFalse(app.context().hasValidSession());
    }
}
