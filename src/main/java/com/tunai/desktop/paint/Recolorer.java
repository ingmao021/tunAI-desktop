package com.tunai.desktop.paint;

/**
 * Recolorea la pintura de la textura (§5.5). Cambia el tono y la saturación al color elegido y
 * conserva las variaciones de luminosidad de cada píxel (detalles, reflejos, líneas). La
 * luminosidad media de la pintura se desplaza hacia la del color elegido para que, por ejemplo,
 * un carro blanco pueda quedar rojo.
 */
public final class Recolorer {

    private final int[] original;
    private final float[] mask;
    /** Luminosidad HSL media de la pintura original. */
    private final double paintLightness;

    public Recolorer(int[] original, float[] mask) {
        this.original = original;
        this.mask = mask;
        this.paintLightness = meanLightness(original, mask);
    }

    /**
     * @param targetRgb color elegido {@code 0xRRGGBB}
     * @param intensity mezcla entre el color nuevo (1) y el original (0)
     * @return nueva textura ARGB; la máscara en cero deja el píxel intacto
     */
    public int[] apply(int targetRgb, double intensity) {
        double[] target = ColorSpaces.rgbToHsl(targetRgb, new double[3]);
        double shift = target[2] - paintLightness;
        double[] hsl = new double[3];
        int[] out = new int[original.length];
        for (int i = 0; i < original.length; i++) {
            int src = original[i];
            double w = mask[i] * intensity;
            if (w <= 0) {
                out[i] = src;
                continue;
            }
            ColorSpaces.rgbToHsl(src, hsl);
            double l = clamp01(hsl[2] + shift);
            int recolored = ColorSpaces.hslToRgb(target[0], target[1], l);
            out[i] = (src & 0xFF000000) | mix(src, recolored, w);
        }
        return out;
    }

    public boolean hasPaint() {
        for (float w : mask) {
            if (w > 0) {
                return true;
            }
        }
        return false;
    }

    private static double meanLightness(int[] argb, float[] mask) {
        double sum = 0;
        double weight = 0;
        double[] hsl = new double[3];
        for (int i = 0; i < argb.length; i++) {
            if (mask[i] > 0) {
                ColorSpaces.rgbToHsl(argb[i], hsl);
                sum += hsl[2] * mask[i];
                weight += mask[i];
            }
        }
        return weight > 0 ? sum / weight : 0.5;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
