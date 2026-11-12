package com.tunai.desktop.viewer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El modelo real de {@code docs/modelo.glb} (48 MB, PBR de Tripo: textura de color base de 19 MB más normales y
 * metálico/rugosidad, y {@code KHR_materials_specular} sin ser obligatoria). Se omite si el archivo no está.
 */
class SampleModelTest {

    private static final Path SAMPLE = Path.of("docs", "modelo.glb");

    @Test
    void realPbrModelLoadsWithTheCorrectGeometry() throws Exception {
        Assumptions.assumeTrue(Files.exists(SAMPLE), "docs/modelo.glb no está en este equipo");
        GlbModel model = GlbModel.load(Files.readAllBytes(SAMPLE));

        assertEquals(38009, model.vertexCount());
        assertEquals(50000, model.triangleCount());
        assertEquals(model.points().length, model.normals().length);
        assertEquals(model.vertexCount() * 2, model.texCoords().length);
        for (int index : model.indices()) {
            assertTrue(index >= 0 && index < model.vertexCount());
        }
        // Textura de color base: el PNG de ~19 MB, no la de normales ni la de metálico.
        byte[] png = model.textureBytes();
        assertTrue(png.length > 15_000_000);
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
    }

    @Test
    void realModelIsCenteredAndScaledToTheScene() throws Exception {
        Assumptions.assumeTrue(Files.exists(SAMPLE), "docs/modelo.glb no está en este equipo");
        float[] p = GlbModel.load(Files.readAllBytes(SAMPLE)).points();
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < p.length; i++) {
            min[i % 3] = Math.min(min[i % 3], p[i]);
            max[i % 3] = Math.max(max[i % 3], p[i]);
        }
        float largest = 0;
        for (int a = 0; a < 3; a++) {
            largest = Math.max(largest, max[a] - min[a]);
            assertEquals(0, (min[a] + max[a]) / 2, 0.01f, "Centrado en el eje " + a);
        }
        assertEquals(GlbModel.TARGET_SIZE, largest, 0.01f);
        // Es un personaje: más alto (Y) que ancho, justo el caso que se cortaba en el visor.
        assertTrue(max[1] - min[1] >= max[0] - min[0]);
        assertTrue(ModelViewer.fitDistance(p) > ModelViewer.INITIAL_DISTANCE);
    }

    @Test
    void realModelPassesValidationAndSizeLimit() throws Exception {
        Assumptions.assumeTrue(Files.exists(SAMPLE), "docs/modelo.glb no está en este equipo");
        GlbModel.validate(Files.readAllBytes(SAMPLE));
        assertTrue(Files.size(SAMPLE) <= 50_000_000L, "Cabe en el límite de importación de 50 MB");
    }
}
