package com.tunai.desktop.ui;

import com.tunai.desktop.App;
import com.tunai.desktop.garage.GarageVehicle;
import com.tunai.desktop.viewer.ModelViewer;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El modelo real de {@code docs/modelo.glb} de punta a punta en el visor: carga, encuadre completo, girar, zoom,
 * pintar y guardar. Se omite si el archivo no está (48 MB, tarda unos segundos).
 *
 * <p>Los botones se accionan con {@code fire()} y los gestos de ratón (girar, zoom) se repiten hasta que la imagen
 * cambia: con el escritorio ocupado Windows puede gastar un gesto en activar la ventana; no depende de la app.
 */
class ViewerSampleModelUiTest extends FxRobot {

    private static final Path SAMPLE = Path.of("docs", "modelo.glb");
    /** Píxeles (muestreados de 2 en 2) que deben cambiar para considerar que la imagen es otra. */
    private static final long CHANGED = 500;

    @TempDir
    Path home;

    private App app;
    private GarageVehicle vehicle;
    private String testName = "test";
    private int shots;

    @BeforeEach
    void setUp(TestInfo info) throws Exception {
        Assumptions.assumeTrue(Files.exists(SAMPLE), "docs/modelo.glb no está en este equipo");
        testName = info.getTestMethod().map(m -> m.getName()).orElse("test");
        app = UiTestApp.start(home, true);
        WaitForAsyncUtils.waitForFxEvents();
        vehicle = VehicleImport.importFile(app.context().garage(), SAMPLE);
        WaitForAsyncUtils.asyncFx(() -> app.context().navigator().<ViewerController>goTo(
                Navigator.Screen.VIEWER, c -> c.open(vehicle))).get(5, TimeUnit.SECONDS);
        // La textura de 19 MB y la máscara de pintura tardan unos segundos.
        WaitForAsyncUtils.waitFor(90, TimeUnit.SECONDS, () ->
                !lookup("#loadingIndicator").queryAs(ProgressIndicator.class).isVisible());
        WaitForAsyncUtils.waitFor(90, TimeUnit.SECONDS,
                () -> lookup(".swatch").queryAll().stream().noneMatch(Node::isDisabled));
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (app != null) {
            FxToolkit.cleanupApplication(app);
        }
    }

    private ModelViewer viewer() {
        return lookup(n -> n instanceof ModelViewer).queryAs(ModelViewer.class);
    }

    /** Captura del visor (la misma que usan Exportar PNG y la miniatura); queda en target/ui-snapshots. */
    private WritableImage snapshot() throws Exception {
        WritableImage img = WaitForAsyncUtils.asyncFx(() -> viewer().snapshot()).get(10, TimeUnit.SECONDS);
        Path dir = Path.of("target", "ui-snapshots");
        Files.createDirectories(dir);
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png",
                dir.resolve("viewer-" + testName + "-" + (++shots) + ".png").toFile());
        return img;
    }

    private void fire(Button b) throws Exception {
        WaitForAsyncUtils.asyncFx(b::fire).get(5, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void fire(ToggleButton b) throws Exception {
        WaitForAsyncUtils.asyncFx(b::fire).get(5, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Repite {@code gesture} hasta que la captura difiera de {@code reference}; devuelve esa captura. */
    private WritableImage repeatUntilChanged(WritableImage reference, Runnable gesture) throws Exception {
        WritableImage last = reference;
        for (int attempt = 0; attempt < 4; attempt++) {
            gesture.run();
            for (int wait = 0; wait < 10; wait++) {
                WaitForAsyncUtils.sleep(300, TimeUnit.MILLISECONDS);
                last = snapshot();
                if (differentPixels(reference, last) > CHANGED) {
                    return last;
                }
            }
        }
        return last;
    }

    private static double minBrightness(WritableImage img, int y) {
        PixelReader px = img.getPixelReader();
        double min = 1;
        for (int x = 0; x < (int) img.getWidth(); x++) {
            min = Math.min(min, px.getColor(x, y).getBrightness());
        }
        return min;
    }

    private static long differentPixels(WritableImage a, WritableImage b) {
        PixelReader pa = a.getPixelReader();
        PixelReader pb = b.getPixelReader();
        long diff = 0;
        for (int y = 0; y < (int) a.getHeight(); y += 2) {
            for (int x = 0; x < (int) a.getWidth(); x += 2) {
                if (Math.abs(pa.getColor(x, y).getBrightness() - pb.getColor(x, y).getBrightness()) > 0.08) {
                    diff++;
                }
            }
        }
        return diff;
    }

    private Bounds stageBounds() {
        Node host = lookup(".viewer-stage").query();
        return host.localToScreen(host.getBoundsInLocal());
    }

    @Test
    void theTallModelFitsInsideTheViewport() throws Exception {
        WritableImage img = snapshot();
        int last = (int) img.getHeight() - 1;
        // Arriba y abajo solo hay fondo claro (degradado de #ffffff a #d5dbe3): nada de modelo cortado.
        assertTrue(minBrightness(img, 0) > 0.8, "El modelo toca el borde superior");
        assertTrue(minBrightness(img, last) > 0.8, "El modelo toca el borde inferior");
        assertTrue(minBrightness(img, 3) > 0.8);
        assertTrue(minBrightness(img, last - 3) > 0.8);
    }

    @Test
    void backgroundHasNoGrayWedgeArtifact() throws Exception {
        WritableImage img = snapshot();
        PixelReader px = img.getPixelReader();
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        // El degradado es simétrico a izquierda y derecha: con el relleno del SubScene aparecía un polígono gris
        // solo en el lado derecho.
        for (int y : new int[]{4, h / 2, h - 5}) {
            double left = px.getColor(4, y).getBrightness();
            double right = px.getColor(w - 5, y).getBrightness();
            assertEquals(left, right, 0.04, "Asimetría del fondo en y=" + y);
        }
    }

    @Test
    void draggingRotatesTheModelAndResetViewRestoresIt() throws Exception {
        WritableImage initial = snapshot();
        Bounds b = stageBounds();
        double cx = b.getMinX() + b.getWidth() / 2;
        double cy = b.getMinY() + b.getHeight() / 3;
        WritableImage rotated = repeatUntilChanged(initial,
                () -> drag(cx, cy, MouseButton.PRIMARY).moveTo(cx + 225, cy).drop());
        assertTrue(differentPixels(initial, rotated) > CHANGED, "Girar con el ratón debe cambiar la imagen");

        fire(lookup("Vista inicial").queryButton());
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        assertTrue(differentPixels(initial, snapshot()) < 200, "Vista inicial vuelve al encuadre de partida");
    }

    @Test
    void zoomChangesTheImage() throws Exception {
        WritableImage initial = snapshot();
        Bounds b = stageBounds();
        WritableImage zoomed = repeatUntilChanged(initial, () -> {
            moveTo(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2);
            scroll(-10);
        });
        assertTrue(differentPixels(initial, zoomed) > CHANGED, "El zoom cambia la imagen");
    }

    @Test
    void autoRotateKeepsTurningTheModel() throws Exception {
        WritableImage before = snapshot();
        ToggleButton auto = lookup("Auto").queryAs(ToggleButton.class);
        fire(auto);
        WritableImage later = repeatUntilChanged(before, () -> {
        });
        assertTrue(differentPixels(before, later) > CHANGED, "Con Auto el modelo gira solo");
        fire(auto);
    }

    @Test
    void paintingMarksTheVehicleAsUnsavedAndSavingWritesTheEditedTexture() throws Exception {
        assertTrue(vehicle.editedTexture().isEmpty());
        WritableImage original = snapshot();
        ToggleButton red = (ToggleButton) lookup(".swatch").queryAll().iterator().next();
        fire(red);
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> lookup("· sin guardar").tryQuery().isPresent()
                && lookup("· sin guardar").query().isVisible());
        WritableImage painted = repeatUntilChanged(original, () -> {
        });
        assertTrue(differentPixels(original, painted) > CHANGED, "La pintura cambia el aspecto del modelo");

        fire(lookup("Guardar").queryButton());
        WaitForAsyncUtils.waitFor(90, TimeUnit.SECONDS, () -> vehicle.editedTexture().isPresent());
        assertNotEquals(0, Files.size(vehicle.editedTexture().get()));
    }
}
