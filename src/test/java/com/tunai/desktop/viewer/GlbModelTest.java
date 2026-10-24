package com.tunai.desktop.viewer;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlbModelTest {

    static byte[] resource(String name) throws Exception {
        try (InputStream in = GlbModelTest.class.getResourceAsStream("/glb/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void loadsSingleTextureModel() throws Exception {
        GlbModel model = GlbModel.load(resource("BoxTextured.glb"));
        assertEquals(24, model.vertexCount());
        assertEquals(12, model.triangleCount());
        assertEquals(model.points().length, model.normals().length);
        assertEquals(model.vertexCount() * 2, model.texCoords().length);
        assertTrue(model.textureBytes().length > 0);

        float maxExtent = 0;
        for (float v : model.points()) {
            maxExtent = Math.max(maxExtent, Math.abs(v));
        }
        assertEquals(GlbModel.TARGET_SIZE / 2, maxExtent, 0.01f, "Centrado y escalado");
    }

    @Test
    void rejectsModelWithoutTexture() {
        assertThrows(UnsupportedModelException.class, () -> GlbModel.load(resource("Box.glb")));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(UnsupportedModelException.class, () -> GlbModel.validate(new byte[]{1, 2, 3, 4}));
    }

    /** CarConcept (lo que devuelve el backend fake) tiene 97 mallas: debe rechazarse. Opcional. */
    @Test
    void rejectsCarConceptWhenAvailable() throws Exception {
        String path = System.getProperty("tunai.test.carconcept");
        if (path == null || !Files.exists(Path.of(path))) {
            return;
        }
        UnsupportedModelException e = assertThrows(UnsupportedModelException.class,
                () -> GlbModel.validate(Files.readAllBytes(Path.of(path))));
        assertTrue(e.getMessage().contains("malla"), e.getMessage());
    }
}
