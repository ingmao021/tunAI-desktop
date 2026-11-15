package com.tunai.desktop.paint;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecolorTest {

    private static final int RED = 0xFFC62828;
    private static final int DARK_RED = 0xFF8E1C1C;
    private static final int BLACK = 0xFF101010;
    private static final int BLUE = 0x1565C0;
    /** Colores de la paleta de la app con los que se comprueba la fidelidad. */
    private static final int[] SWATCHES = {0xD32F2F, 0x1565C0, 0xEF6C00, 0x212121, 0xFAFAFA, 0x4D001A, 0x2E7D32};

    /** Textura: 70 % pintura roja (dos tonos), 20 % vidrio negro, 10 % fondo sin usar. */
    private static int[] texture() {
        int[] px = new int[100];
        for (int i = 0; i < 100; i++) {
            px[i] = i < 50 ? RED : i < 70 ? DARK_RED : i < 90 ? BLACK : 0xFF000000;
        }
        return px;
    }

    /** Carrocería gris con el sombreado de una textura real (L* de ~50 a ~74): 100 píxeles de oscuro a claro. */
    private static int[] shadedGrayBody() {
        int[] px = new int[100];
        for (int i = 0; i < 100; i++) {
            int level = 0x78 + i * 0x40 / 99;
            px[i] = 0xFF000000 | (level << 16) | (level << 8) | level;
        }
        return px;
    }

    private static float[] fullMask(int n) {
        float[] mask = new float[n];
        Arrays.fill(mask, 1f);
        return mask;
    }

    private static double[] lab(int rgb) {
        return ColorSpaces.rgbToLab(rgb & 0xFFFFFF, new double[3]);
    }

    private static double deltaE(double[] a, double[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }

    private static double[] meanLab(int[] pixels) {
        double[] mean = new double[3];
        for (int p : pixels) {
            double[] l = lab(p);
            for (int k = 0; k < 3; k++) {
                mean[k] += l[k] / pixels.length;
            }
        }
        return mean;
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
        Arrays.fill(px, 0xFF000000);                  // fondo del atlas, mayoritario
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

        double[] target = lab(BLUE);
        double[] painted = lab(out[0]);
        double hueDiff = Math.abs(Math.toDegrees(Math.atan2(painted[2], painted[1]) - Math.atan2(target[2], target[1])));
        assertTrue(Math.min(hueDiff, 360 - hueDiff) < 4, "Tono azul");
        assertTrue(painted[0] > lab(out[60])[0], "Se conserva la diferencia de luminosidad entre tonos");
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
        Arrays.fill(tex, 0xFFF2F2F2);
        Recolorer recolorer = new Recolorer(tex, new BodyPaintMaskStrategy().computeMask(tex, 10, 10, null));
        double[] painted = lab(recolorer.apply(0xC62828, 1)[0]);
        assertTrue(Math.hypot(painted[1], painted[2]) > 40, "Un carro blanco también toma el color");
    }

    /** El color que se ve en promedio es el que se eligió, aunque la pintura original sea más clara u oscura. */
    @Test
    void averagePaintColorMatchesTheChosenColor() {
        int[] tex = shadedGrayBody();
        Recolorer recolorer = new Recolorer(tex, fullMask(tex.length));
        for (int swatch : SWATCHES) {
            double dE = deltaE(meanLab(recolorer.apply(swatch, 1.0)), lab(swatch));
            assertTrue(dE <= 4, String.format("ΔE de %06X = %.1f", swatch, dE));
        }
    }

    @Test
    void shadingIsKeptRelativeToThePaint() {
        int[] tex = shadedGrayBody();
        int[] out = new Recolorer(tex, fullMask(tex.length)).apply(BLUE, 1.0);
        double before = relativeSpread(tex);
        double after = relativeSpread(out);
        assertTrue(after >= 0.7 * before, String.format("Contraste relativo %.2f → %.2f", before, after));
        assertTrue(lab(out[99])[0] > lab(out[0])[0], "La zona clara sigue más clara que la oscura");
    }

    @Test
    void darkAndLightTargetsStayDarkAndLight() {
        int[] tex = shadedGrayBody();
        Recolorer recolorer = new Recolorer(tex, fullMask(tex.length));
        assertTrue(meanLab(recolorer.apply(0x212121, 1.0))[0] < 16, "Un negro sobre carrocería clara sigue siendo negro");
        assertTrue(meanLab(recolorer.apply(0xFAFAFA, 1.0))[0] > 92, "Un blanco sobre carrocería oscura sigue siendo blanco");
    }

    @Test
    void partialMaskBlendsWithTheOriginal() {
        int[] tex = new int[100];
        Arrays.fill(tex, 0xFF808080);
        float[] mask = new float[100];
        mask[0] = 1f;
        mask[1] = 0.5f;
        int[] out = new Recolorer(tex, mask).apply(0xC62828, 1.0);
        double[] full = lab(out[0]);
        double[] half = lab(out[1]);
        double[] none = lab(out[2]);
        assertEquals(lab(0x808080)[0], none[0], 1e-6, "Sin máscara el píxel no cambia");
        assertTrue(Math.hypot(half[1], half[2]) > 0.2 * Math.hypot(full[1], full[2])
                && Math.hypot(half[1], half[2]) < 0.9 * Math.hypot(full[1], full[2]), "El borde queda a medias");
    }

    @Test
    void alphaIsKept() {
        int[] tex = {0x80C62828};
        int[] out = new Recolorer(tex, new float[]{1f}).apply(BLUE, 1.0);
        assertEquals(0x80, out[0] >>> 24);
    }

    @Test
    void hasPaintOnlyWhenTheMaskSelectsSomething() {
        int[] tex = texture();
        assertTrue(new Recolorer(tex, fullMask(tex.length)).hasPaint());
        assertFalse(new Recolorer(tex, new float[tex.length]).hasPaint());
    }

    @Test
    void uvCoverageFillsTriangles() {
        float[] uv = {0, 0, 1, 0, 0, 1};
        boolean[] cov = UvCoverage.compute(uv, new int[]{0, 1, 2}, 10, 10);
        assertTrue(cov[0]);
        assertTrue(cov[2 * 10 + 2]);
        assertTrue(!cov[9 * 10 + 9], "La esquina opuesta queda fuera");
    }

    /** Zócalos y mentones inclinados son pintura; solo el piso (casi plano hacia abajo) se descarta. */
    @Test
    void uvCoverageDropsOnlyTrianglesFacingStraightDown() {
        float[] uv = {0, 0, 1, 0, 0, 1};
        int[] triangle = {0, 1, 2};
        float[] slanted = new float[9];
        float[] floor = new float[9];
        for (int v = 0; v < 3; v++) {
            slanted[v * 3 + 1] = 0.7f;     // Y hacia abajo en JavaFX
            slanted[v * 3 + 2] = 0.714f;
            floor[v * 3 + 1] = 1f;
        }
        assertTrue(UvCoverage.compute(uv, triangle, slanted, 10, 10)[0], "Una pieza inclinada sigue siendo carrocería");
        assertFalse(UvCoverage.compute(uv, triangle, floor, 10, 10)[0], "El piso no es pintura");
    }

    @Test
    void linearLightRoundTripKeepsTheColor() {
        for (int rgb : new int[]{0xC62828, 0x1565C0, 0x2E7D32, 0xFFFFFF, 0x000000, 0x9E9E9E, 0x212121, 0xFAFAFA}) {
            double[] linear = ColorSpaces.linearRgb(rgb, new double[3]);
            int back = ColorSpaces.linearToRgb(linear[0], linear[1], linear[2]);
            for (int shift : new int[]{16, 8, 0}) {
                assertEquals((rgb >> shift) & 0xFF, (back >> shift) & 0xFF, 1, String.format("%06X", rgb));
            }
        }
    }

    @Test
    void lightnessHelpersAgreeWithLab() {
        for (int rgb : new int[]{0xC62828, 0x1565C0, 0xFFFFFF, 0x000000, 0x212121, 0x9E9E9E}) {
            assertEquals(lab(rgb)[0], ColorSpaces.lightness(rgb), 1e-9);
            double[] linear = ColorSpaces.linearRgb(rgb, new double[3]);
            double y = ColorSpaces.luminance(linear[0], linear[1], linear[2]);
            assertEquals(y, ColorSpaces.luminanceFromLightness(lab(rgb)[0]), 1e-6, "L* → Y es la inversa");
        }
    }

    @Test
    void mixingHappensInLinearLight() {
        int mid = ColorSpaces.mixLinear(0x000000, 0xFFFFFF, 0.5);
        assertEquals(188, (mid >> 8) & 0xFF, 1, "La mitad de luz no es el gris 128 de sRGB");
        assertEquals(0xFFFFFF, ColorSpaces.mixLinear(0x000000, 0xFFFFFF, 1.0));
        assertEquals(0x000000, ColorSpaces.mixLinear(0x000000, 0xFFFFFF, 0.0));
    }

    /** Desviación de la luminosidad dividida por su media: el contraste relativo del sombreado. */
    private static double relativeSpread(int[] pixels) {
        double sum = 0;
        double sumSquares = 0;
        for (int p : pixels) {
            double l = ColorSpaces.lightness(p & 0xFFFFFF);
            sum += l;
            sumSquares += l * l;
        }
        double mean = sum / pixels.length;
        return Math.sqrt(Math.max(0, sumSquares / pixels.length - mean * mean)) / mean;
    }
}
