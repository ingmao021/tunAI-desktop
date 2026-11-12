package com.tunai.desktop.ui;

import com.tunai.desktop.App;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Marco de la app: sidebar compacto/expandido, navegación por sus ítems y atajo Ctrl+K de la paleta. */
class SidebarShellUiTest extends FxRobot {

    @TempDir
    Path home;

    private App app;

    @BeforeEach
    void setUp() throws Exception {
        app = UiTestApp.start(home, true);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterEach
    void tearDown() throws Exception {
        FxToolkit.cleanupApplication(app);
    }

    private SidebarShell shell() {
        return assertInstanceOf(SidebarShell.class, app.context().navigator().scene().getRoot());
    }

    private void fx(Runnable r) throws Exception {
        WaitForAsyncUtils.asyncFx(r).get(5, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Button item(String accessibleText) {
        return shell().lookupAll(".sidebar-item").stream()
                .filter(n -> n instanceof Button b && accessibleText.equals(b.getAccessibleText()))
                .map(n -> (Button) n).findFirst().orElseThrow();
    }

    @Test
    void onlyAppScreensAreWrappedInTheFrame() {
        assertTrue(SidebarShell.wraps(Navigator.Screen.GARAGE));
        assertTrue(SidebarShell.wraps(Navigator.Screen.NEW_VEHICLE));
        assertTrue(SidebarShell.wraps(Navigator.Screen.GENERATING));
        assertTrue(SidebarShell.wraps(Navigator.Screen.VIEWER));
        assertTrue(SidebarShell.wraps(Navigator.Screen.SETTINGS));
        assertFalse(SidebarShell.wraps(Navigator.Screen.LOGIN));
        assertFalse(SidebarShell.wraps(Navigator.Screen.CONSENT));
    }

    @Test
    void startsCompactWithTheGarageItemActive() {
        Node sidebar = shell().lookup(".sidebar");
        assertEquals(SidebarShell.COLLAPSED, ((javafx.scene.layout.Region) sidebar).getWidth(), 0.5);
        assertTrue(item("Mi garaje").getStyleClass().contains("active"));
        assertFalse(item("Ajustes").getStyleClass().contains("active"));
    }

    @Test
    void toggleExpandsAndCollapsesAndRemembersTheChoice() throws Exception {
        fx(() -> shell().toggle());
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        assertTrue(app.context().settings().get().sidebarExpanded);
        assertEquals(SidebarShell.EXPANDED, ((javafx.scene.layout.Region) shell().lookup(".sidebar")).getWidth(), 0.5);
        assertEquals(javafx.scene.control.ContentDisplay.LEFT, item("Mi garaje").getContentDisplay());

        fx(() -> shell().toggle());
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        assertFalse(app.context().settings().get().sidebarExpanded);
        assertEquals(SidebarShell.COLLAPSED, ((javafx.scene.layout.Region) shell().lookup(".sidebar")).getWidth(), 0.5);
        assertEquals(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY, item("Mi garaje").getContentDisplay());
    }

    @Test
    void settingsItemOpensTheSettingsScreenAndMarksItself() throws Exception {
        fx(() -> item("Ajustes").fire());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> app.context().navigator().currentController() instanceof SettingsController);
        assertTrue(item("Ajustes").getStyleClass().contains("active"));
        assertFalse(item("Mi garaje").getStyleClass().contains("active"));

        fx(() -> item("Mi garaje").fire());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> app.context().navigator().currentController() instanceof GarageController);
    }

    @Test
    void ctrlKOpensThePaletteAndEscapeClosesIt() throws Exception {
        CommandPalette palette = (CommandPalette) shell().lookup(".palette-scrim");
        assertNotNull(palette);
        assertFalse(palette.isOpen());
        Scene scene = app.context().navigator().scene();
        fx(() -> javafx.event.Event.fireEvent(scene,
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.K, false, true, false, false)));
        assertTrue(palette.isOpen());
        fx(() -> palette.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                false, false, false, false)));
        assertFalse(palette.isOpen());
        Platform.runLater(() -> {
        });
    }

    @Test
    void ctrlBTogglesTheSidebar() throws Exception {
        Scene scene = app.context().navigator().scene();
        fx(() -> javafx.event.Event.fireEvent(scene,
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.B, false, true, false, false)));
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        assertTrue(app.context().settings().get().sidebarExpanded);
    }
}
