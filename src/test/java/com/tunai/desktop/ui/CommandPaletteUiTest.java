package com.tunai.desktop.ui;

import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Paleta de comandos: abrir, filtrar sin tildes, moverse con flechas, ejecutar con Enter y cerrar con Esc. */
class CommandPaletteUiTest {

    private final List<String> ran = new ArrayList<>();
    private CommandPalette palette;
    private TextField field;
    private ListView<CommandPalette.Command> list;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        ran.clear();
        FxToolkit.registerPrimaryStage();
        FxToolkit.setupStage(stage -> {
            palette = new CommandPalette(() -> List.of(
                    command("Mi garaje"), command("Ajustes"), command("Nuevo vehículo"), command("Camión rojo")));
            stage.setScene(new Scene(palette, 800, 600));
            stage.show();
        });
        field = (TextField) palette.lookup(".palette-field");
        list = (ListView<CommandPalette.Command>) palette.lookup(".palette-list");
    }

    private CommandPalette.Command command(String title) {
        return new CommandPalette.Command("Ir a", title, "mdal-add", "Acción", () -> ran.add(title));
    }

    private void fx(Runnable r) throws Exception {
        WaitForAsyncUtils.asyncFx(r).get(5, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void press(KeyCode code) throws Exception {
        fx(() -> field.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false)));
    }

    @Test
    void startsClosedAndOpensWithEveryCommand() throws Exception {
        assertFalse(palette.isOpen());
        fx(palette::open);
        assertTrue(palette.isOpen());
        assertEquals(4, list.getItems().size());
        assertEquals(0, list.getSelectionModel().getSelectedIndex());
    }

    @Test
    void filtersIgnoringAccentsAndCase() throws Exception {
        fx(palette::open);
        fx(() -> field.setText("VEHICULO"));
        assertEquals(1, list.getItems().size());
        assertEquals("Nuevo vehículo", list.getItems().get(0).title());

        fx(() -> field.setText("camion"));
        assertEquals("Camión rojo", list.getItems().get(0).title());
    }

    @Test
    void noMatchesLeavesTheListEmptyAndEnterDoesNothing() throws Exception {
        fx(palette::open);
        fx(() -> field.setText("zzz"));
        assertTrue(list.getItems().isEmpty());
        press(KeyCode.ENTER);
        assertTrue(ran.isEmpty());
        assertTrue(palette.isOpen());
    }

    @Test
    void arrowsMoveAndWrapAround() throws Exception {
        fx(palette::open);
        press(KeyCode.DOWN);
        assertEquals(1, list.getSelectionModel().getSelectedIndex());
        press(KeyCode.UP);
        press(KeyCode.UP);
        assertEquals(3, list.getSelectionModel().getSelectedIndex(), "Da la vuelta por arriba");
        press(KeyCode.DOWN);
        assertEquals(0, list.getSelectionModel().getSelectedIndex(), "Da la vuelta por abajo");
    }

    @Test
    void enterRunsTheSelectedCommandAndCloses() throws Exception {
        fx(palette::open);
        press(KeyCode.DOWN);
        press(KeyCode.ENTER);
        assertEquals(List.of("Ajustes"), ran);
        assertFalse(palette.isOpen());
    }

    @Test
    void escapeClosesWithoutRunningAnything() throws Exception {
        fx(palette::open);
        fx(() -> palette.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                false, false, false, false)));
        assertFalse(palette.isOpen());
        assertTrue(ran.isEmpty());
    }

    @Test
    void reopeningClearsThePreviousSearch() throws Exception {
        fx(palette::open);
        fx(() -> field.setText("ajust"));
        fx(palette::close);
        fx(palette::open);
        assertEquals("", field.getText());
        assertEquals(4, list.getItems().size());
    }

    @Test
    void toggleOpensAndCloses() throws Exception {
        fx(palette::toggle);
        assertTrue(palette.isOpen());
        fx(palette::toggle);
        assertFalse(palette.isOpen());
    }
}
