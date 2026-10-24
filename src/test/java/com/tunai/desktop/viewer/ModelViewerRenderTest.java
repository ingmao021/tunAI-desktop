package com.tunai.desktop.viewer;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CA-UI-07: el modelo se ve con textura. Guarda capturas en {@code target/ui-snapshots}. */
class ModelViewerRenderTest extends FxRobot {

    @Test
    void rendersTexturedModelAndClampsZoom() throws Exception {
        FxToolkit.registerPrimaryStage();
        GlbModel model = GlbModel.load(GlbModelTest.resource("BoxTextured.glb"));
        AtomicReference<ModelViewer> viewer = new AtomicReference<>();
        FxToolkit.setupStage(s -> {
            ModelViewer v = new ModelViewer();
            v.setModel(model, new Image(new ByteArrayInputStream(model.textureBytes())));
            viewer.set(v);
            s.setScene(new Scene(v, 640, 480));
            s.show();
        });
        WaitForAsyncUtils.waitForFxEvents();

        WritableImage shot = snapshot(viewer.get());
        save(shot, "viewer-boxtextured.png");
        // El centro de la imagen debe ser el modelo, no el fondo.
        int center = shot.getPixelReader().getArgb(320, 240) & 0xFFFFFF;
        assertTrue(center != 0xEEF1F5, "El centro muestra el fondo: el modelo no se dibujó");

        // Opcional: el modelo real de prueba (por ejemplo Duck.glb) para revisión visual.
        String extra = System.getProperty("tunai.test.glb");
        if (extra != null && Files.exists(Path.of(extra))) {
            GlbModel other = GlbModel.load(Files.readAllBytes(Path.of(extra)));
            FxToolkit.setupFixture(() -> viewer.get().setModel(other,
                    new Image(new ByteArrayInputStream(other.textureBytes()))));
            WaitForAsyncUtils.waitForFxEvents();
            save(snapshot(viewer.get()), "viewer-extra.png");
        }

        FxToolkit.setupFixture(() -> {
            for (int i = 0; i < 50; i++) {
                viewer.get().zoom(1000);
            }
        });
        assertEquals(ModelViewer.MIN_DISTANCE, viewer.get().distance(), 1e-6);
        FxToolkit.setupFixture(() -> {
            for (int i = 0; i < 50; i++) {
                viewer.get().zoom(-1000);
            }
        });
        assertEquals(ModelViewer.MAX_DISTANCE, viewer.get().distance(), 1e-6);
        FxToolkit.hideStage();
    }

    private static WritableImage snapshot(ModelViewer v) throws Exception {
        AtomicReference<WritableImage> img = new AtomicReference<>();
        FxToolkit.setupFixture(() -> img.set(v.snapshot()));
        return img.get();
    }

    private static void save(WritableImage image, String name) throws Exception {
        Path dir = Files.createDirectories(Path.of("target", "ui-snapshots"));
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", dir.resolve(name).toFile());
    }
}
