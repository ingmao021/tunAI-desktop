package com.tunai.desktop.paint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecolorTest {

    private static final int RED = 0xFFC62828;
    private static final int DARK_RED = 0xFF8E1C1C;
    private static final int BLACK = 0xFF101010;
    private static final int BLUE = 0x1565C0;

    /** Textura: 70 % pintura roja (dos tonos), 20 % vidrio negro, 10 % fondo sin usar. */
    private static int[] texture() {
        int[] px = new int[100];
        for (int i = 0; i < 100; i++) {
            px[i] = i < 50 ? RED : i < 70 ? DARK_RED : i < 90 ? BLACK : 0xFF000000;
        }
        return px;
    }

    @Test
    void silverBodyIsSeparatedFromGlassAndTires() {
        int[] px = new int[100];
        for (int i = 0; i < 100; i++) {
            // 45 % plata visible, 20 % gris claro inventado por SF3D, 25 % vidrio, 10 % llantas
            px[i] = i < 45 ? 0xFFD9DADC : i < 65 ? 0xFFA9AAAC : i < 90 ? 0xFF3A3D40 : 0xFF161616;
        }
        float[] mask = new BodyPaintMaskStrategy().computeMask(px, 10, 10, null);
        assertEquals(1f, mask[0], 1e-6);
        assertTrue(mask[50] > 0.9f, "El gris claro de las caras no vistas también es carrocería");
        assertEquals(0f, mask[70], 1e-6, "El vidrio no es pintura");
        assertEquals(0f, mask[95], 1e-6, "Las llantas no son pintura");
    }

    @Test
    void maskSelectsDominantPaintOnly() {
        float[] mask = new BodyPaintMaskStrategy().computeMask(texture(), 10, 10, null);
        assertEquals(1f, mask[0], 1e-6);
        assertTrue(mask[60] > 0.5f, "El rojo oscuro también es pintura");
        assertEquals(0f, mask[80], 1e-6, "El vidrio negro no es pintura");
    }

    @Test
    void coverageExcludesUnusedAtlasPixels() {
        int[] px = new int[100];
        java.util.Arrays.fill(px, 0xFF000000);       // fondo del atlas, mayoritario
        for (int i = 0; i < 30; i++) {
            px[i] = RED;                              // pintura dentro de las islas UV
        }
        boolean[] coverage = new boolean[100];
        for (int i = 0; i < 40; i++) {
            coverage[i] = true;
        }
        float[] mask = new BodyPaintMaskStrategy().computeMask(px, 10, 10, coverage);
        assertEquals(1f, mask[0], 1e-6);
        assertEquals(0f, mask[35], 1e-6, "Píxeles negros dentro de las islas");
        assertEquals(0f, mask[90], 1e-6);
    }

    @Test
    void recolorChangesHueAndKeepsDetail() {
        int[] tex = texture();
        Recolorer recolorer = new Recolorer(tex, new BodyPaintMaskStrategy().computeMask(tex, 10, 10, null));
        int[] out = recolorer.apply(BLUE, 1.0);

        double[] hsl = ColorSpaces.rgbToHsl(out[0], new double[3]);
        assertEquals(ColorSpaces.rgbToHsl(BLUE, new double[3])[0], hsl[0], 2.0, "Tono azul");
        double light = ColorSpaces.rgbToHsl(out[0], new double[3])[2];
        double dark = ColorSpaces.rgbToHsl(out[60], new double[3])[2];
        assertTrue(light > dark, "Se conserva la diferencia de luminosidad entre tonos");
        assertEquals(BLACK, out[80], "El vidrio no cambia");
    }

    @Test
    void zeroIntensityRestoresOriginal() {
        int[] tex = texture();
        Recolorer recolorer = new Recolorer(tex, new BodyPaintMaskStrategy().computeMask(tex, 10, 10, null));
        assertArrayEquals(tex, recolorer.apply(BLUE, 0));
    }

    @Test
    void whitePaintCanBecomeColored() {
        int[] tex = new int[100];
        java.util.Arrays.fill(tex, 0xFFF2F2F2);
        Recolorer recolorer = new Recolorer(tex, new BodyPaintMaskStrategy().computeMask(tex, 10, 10, null));
        double[] hsl = ColorSpaces.rgbToHsl(recolorer.apply(0xC62828, 1)[0], new double[3]);
        assertTrue(hsl[1] > 0.5, "Un carro blanco también toma el color");
    }

    @Test
    void uvCoverageFillsTriangles() {
        float[] uv = {0, 0, 1, 0, 0, 1};
        boolean[] cov = UvCoverage.compute(uv, new int[]{0, 1, 2}, 10, 10);
        assertTrue(cov[0]);
        assertTrue(cov[2 * 10 + 2]);
        assertTrue(!cov[9 * 10 + 9], "La esquina opuesta queda fuera");
    }

    @Test
    void hslRoundTrip() {
        for (int rgb : new int[]{0xC62828, 0x1565C0, 0x2E7D32, 0xFFFFFF, 0x000000, 0x9E9E9E}) {
            double[] hsl = ColorSpaces.rgbToHsl(rgb, new double[3]);
            assertEquals(rgb, ColorSpaces.hslToRgb(hsl[0], hsl[1], hsl[2]));
        }
    }
}
