package com.tunai.desktop.paint;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintIslandFilterTest {

    private static final int SIZE = 100;

    /** Piezas sueltas (cada una con sus vértices y su rectángulo de UV) en un atlas de 100×100. */
    private static final class Mesh {
        private final List<Float> points = new ArrayList<>();
        private final List<Float> uvs = new ArrayList<>();
        private final List<Integer> indices = new ArrayList<>();

        /** Cuadrado de {@code size} unidades en el plano XY, dos triángulos, con el rectángulo UV indicado. */
        Mesh quad(float x, float y, float size, float u0, float v0, float u1, float v1) {
            int base = points.size() / 3;
            float[][] p = {{x, y}, {x + size, y}, {x + size, y + size}, {x, y + size}};
            float[][] t = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
            for (int i = 0; i < 4; i++) {
                points.add(p[i][0]);
                points.add(p[i][1]);
                points.add(0f);
                uvs.add(t[i][0]);
                uvs.add(t[i][1]);
            }
            for (int i : new int[]{0, 1, 2, 0, 2, 3}) {
                indices.add(base + i);
            }
            return this;
        }

        float[] points() {
            float[] out = new float[points.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = points.get(i);
            }
            return out;
        }

        float[] uvs() {
            float[] out = new float[uvs.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = uvs.get(i);
            }
            return out;
        }

        int[] indices() {
            return indices.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private static float[] mask(float value) {
        float[] mask = new float[SIZE * SIZE];
        Arrays.fill(mask, value);
        return mask;
    }

    private static boolean[] dropped(Mesh mesh, float[] mask) {
        return PaintIslandFilter.droppedPixels(mask, SIZE, SIZE, mesh.points(), mesh.uvs(), mesh.indices());
    }

    private static int at(int x, int y) {
        return y * SIZE + x;
    }

    /** Cuerpo grande y un rin suelto del mismo color: el rin se descarta y el cuerpo no. */
    @Test
    void smallDisconnectedIslandIsDropped() {
        Mesh mesh = new Mesh()
                .quad(0, 0, 10, 0.0f, 0.0f, 0.5f, 0.5f)       // carrocería, área 100
                .quad(20, 0, 2, 0.6f, 0.6f, 0.7f, 0.7f);      // rin, área 4 (4 % de la mayor)
        boolean[] dropped = dropped(mesh, mask(1f));
        assertNotNull(dropped);
        assertTrue(dropped[at(65, 65)], "Los píxeles del rin se descartan");
        assertFalse(dropped[at(25, 25)], "La carrocería se conserva");
        assertFalse(dropped[at(80, 10)], "Lo que ninguna pieza usa no se toca");
    }

    @Test
    void piecesWithoutPaintAreNotTouched() {
        float[] mask = mask(1f);
        for (int y = 10; y < 20; y++) {
            for (int x = 80; x < 90; x++) {
                mask[at(x, y)] = 0f;                          // el vidrio no es pintura
            }
        }
        Mesh mesh = new Mesh()
                .quad(0, 0, 10, 0.0f, 0.0f, 0.5f, 0.5f)
                .quad(20, 0, 2, 0.6f, 0.6f, 0.7f, 0.7f)
                .quad(40, 0, 4, 0.8f, 0.1f, 0.9f, 0.2f);      // vidrio: máscara en cero
        boolean[] dropped = dropped(mesh, mask);
        assertNotNull(dropped);
        assertTrue(dropped[at(65, 65)]);
        assertFalse(dropped[at(85, 15)], "Una pieza sin pintura no es pintura descartada: ya estaba en cero");
    }

    /** Dos piezas grandes (una carrocería partida por una moldura) se conservan las dos. */
    @Test
    void largeIslandsAreAllKept() {
        Mesh mesh = new Mesh()
                .quad(0, 0, 10, 0.0f, 0.0f, 0.5f, 0.5f)
                .quad(20, 0, 5, 0.6f, 0.6f, 0.9f, 0.9f);      // 25 % de la mayor
        assertNull(dropped(mesh, mask(1f)));
    }

    /** Si lo descartable fuera la mayor parte de la pintura, el modelo no es fiable y no se filtra. */
    @Test
    void filterIsIgnoredWhenItWouldRemoveMostOfThePaint() {
        Mesh mesh = new Mesh().quad(0, 0, 10, 0.0f, 0.0f, 0.05f, 0.05f);   // área 100
        for (int i = 0; i < 40; i++) {
            float u = 0.1f + (i % 8) * 0.1f;
            float v = 0.1f + (i / 8) * 0.1f;
            mesh.quad(20 + i * 5, 0, 2.5f, u, v, u + 0.05f, v + 0.05f);    // 40 piezas de 6,25 → 250 en total
        }
        assertNull(dropped(mesh, mask(1f)));
    }

    @Test
    void meshWithoutPaintIsLeftAlone() {
        Mesh mesh = new Mesh().quad(0, 0, 10, 0.0f, 0.0f, 0.5f, 0.5f);
        assertNull(dropped(mesh, mask(0f)));
    }

    @Test
    void refinerClearsTheDroppedPieceAndSmoothsTheRest() {
        Mesh mesh = new Mesh()
                .quad(0, 0, 10, 0.0f, 0.0f, 0.5f, 0.5f)
                .quad(20, 0, 2, 0.6f, 0.6f, 0.7f, 0.7f);
        float[] mask = mask(1f);
        boolean[] coverage = UvCoverage.compute(mesh.uvs(), mesh.indices(), SIZE, SIZE);

        float[] refined = PaintMaskRefiner.refine(mask, coverage, SIZE, SIZE, mesh.points(), mesh.uvs(), mesh.indices());

        assertNotSame(mask, refined);
        assertEquals(1f, mask[at(65, 65)], "La máscara de entrada no se modifica");
        assertEquals(0f, refined[at(65, 65)], "El rin queda sin pintar");
        assertEquals(1f, refined[at(25, 25)], 1e-6, "El interior de la carrocería no cambia");
        assertEquals(1f, refined[at(1, 1)], 1e-6, "El borde de la isla no se aclara al mezclarse con el vacío del atlas");
    }

    @Test
    void refinerWorksWithoutCoverageOrDroppedPieces() {
        Mesh mesh = new Mesh().quad(0, 0, 10, 0.0f, 0.0f, 0.5f, 0.5f);
        float[] refined = PaintMaskRefiner.refine(mask(1f), null, SIZE, SIZE, mesh.points(), mesh.uvs(), mesh.indices());
        assertEquals(1f, refined[at(25, 25)], 1e-6);
    }

    @Test
    void featherSoftensAHardEdgeWithoutSpillingOutsideTheAllowedPixels() {
        int w = 20;
        int h = 5;
        float[] mask = new float[w * h];
        boolean[] allowed = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mask[y * w + x] = x < 10 ? 1f : 0f;
                allowed[y * w + x] = x < 16;                  // los píxeles 16+ no los usa el modelo
            }
        }
        float[] out = MaskFeather.feather(mask, allowed, w, h);
        int row = 2 * w;
        assertEquals(1f, out[row + 5], 1e-6, "Lejos del borde no cambia");
        assertTrue(out[row + 9] < 1f && out[row + 9] > out[row + 10], "El borde baja poco a poco");
        assertTrue(out[row + 10] > 0f && out[row + 10] < 0.5f);
        assertEquals(0f, out[row + 14], 1e-6, "Lejos del borde, sin pintura, no se mancha");
        assertEquals(0f, out[row + 17], "Fuera de lo permitido no se toca");
    }

    @Test
    void featherKeepsValuesInRange() {
        int w = 12;
        float[] mask = new float[w * w];
        boolean[] allowed = new boolean[w * w];
        Arrays.fill(allowed, true);
        for (int i = 0; i < mask.length; i++) {
            mask[i] = (i * 7 % 10) / 10f;
        }
        for (float v : MaskFeather.feather(mask, allowed, w, w)) {
            assertTrue(v >= 0f && v <= 1f);
        }
    }
}
