package com.tunai.desktop.paint;

import java.util.Arrays;

/**
 * Recolorea la pintura de la textura (§5.5). Cada píxel pintado toma el color elegido y conserva su
 * luminosidad <i>relativa</i> a la pintura (detalles, paneles, sombras horneadas).
 *
 * <p>La luminosidad L* de cada píxel se escala con una ganancia calibrada para que el promedio de la pintura
 * sea exactamente la luminosidad del color elegido (así un carro blanco puede quedar rojo o negro y el color
 * que se ve es el que se eligió). El color de cada nivel de luminosidad es el elegido <i>escalado en luz
 * lineal</i>, como lo haría la iluminación: conserva el tono y la saturación. Un color muy saturado (un
 * naranja) casi no tiene margen para ser más claro sin salirse de sRGB: sus reflejos se aplanan con una
 * rodilla suave en el máximo que cabe, en vez de desteñirse. La mezcla con el original (borde de la máscara
 * o intensidad menor que 1) también se hace en luz lineal.</p>
 */
public final class Recolorer {

    /** Resolución de la tabla luminosidad → color (L* de 0 a 100). */
    private static final int LUT_SIZE = 1024;
    /** Histograma de luminosidad en pasos de 0,25. */
    private static final int HISTOGRAM_BINS = 401;
    private static final double HISTOGRAM_STEP = 0.25;
    /** La rodilla empieza a esta fracción del máximo que admite el color. */
    private static final double KNEE_RATIO = 0.8;
    private static final double MAX_GAIN = 60;
    /** Luminancia mínima del color base: con un negro puro no habría a qué escalar. */
    private static final double MIN_LUMINANCE = 1e-4;
    private static final double FULL_WEIGHT = 0.999;

    private final int[] original;
    private final float[] mask;
    /** Luminosidad L* de la pintura, ponderada por la máscara, por intervalos. */
    private final double[] histogram = new double[HISTOGRAM_BINS];
    private final double paintWeight;

    public Recolorer(int[] original, float[] mask) {
        this.original = original;
        this.mask = mask;
        double weight = 0;
        for (int i = 0; i < original.length; i++) {
            if (mask[i] > 0) {
                histogram[bin(ColorSpaces.lightness(original[i]))] += mask[i];
                weight += mask[i];
            }
        }
        this.paintWeight = weight;
    }

    /**
     * @param targetRgb color elegido {@code 0xRRGGBB}
     * @param intensity mezcla entre el color nuevo (1) y el original (0)
     * @return nueva textura ARGB; la máscara en cero deja el píxel intacto
     */
    public int[] apply(int targetRgb, double intensity) {
        double[] base = ColorSpaces.linearRgb(targetRgb, new double[3]);
        double baseY = ColorSpaces.luminance(base[0], base[1], base[2]);
        if (baseY < MIN_LUMINANCE) {
            Arrays.fill(base, MIN_LUMINANCE);
            baseY = MIN_LUMINANCE;
        }
        // Luminosidad máxima con la que el color elegido todavía cabe en sRGB (con el canal más alto en 1).
        double maxChannel = Math.max(base[0], Math.max(base[1], base[2]));
        double cap = ColorSpaces.lightnessFromLuminance(Math.min(1, baseY / maxChannel));
        double gain = calibratedGain(ColorSpaces.lightness(targetRgb), cap);
        int[] lut = buildLut(base, baseY);

        int[] out = new int[original.length];
        for (int i = 0; i < original.length; i++) {
            int src = original[i];
            double w = mask[i] * intensity;
            if (w <= 0) {
                out[i] = src;
                continue;
            }
            double l = soft(gain * ColorSpaces.lightness(src), cap);
            int recolored = lut[(int) Math.round(l * (LUT_SIZE - 1) / 100)];
            int color = w >= FULL_WEIGHT ? recolored : ColorSpaces.mixLinear(src & 0xFFFFFF, recolored, w);
            out[i] = (src & 0xFF000000) | color;
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

    /** Ganancia de luminosidad con la que el promedio de la pintura recoloreada es {@code targetL}. */
    private double calibratedGain(double targetL, double cap) {
        if (paintWeight <= 0) {
            return 1;
        }
        double lo = 0;
        double hi = MAX_GAIN;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (meanShaded(mid, cap) < targetL) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    private double meanShaded(double gain, double cap) {
        double sum = 0;
        for (int b = 0; b < HISTOGRAM_BINS; b++) {
            if (histogram[b] > 0) {
                sum += histogram[b] * soft(gain * b * HISTOGRAM_STEP, cap);
            }
        }
        return sum / paintWeight;
    }

    /** Tabla L* → color: el elegido escalado en luz lineal hasta tener la luminancia de ese nivel. */
    private static int[] buildLut(double[] base, double baseY) {
        int[] lut = new int[LUT_SIZE];
        for (int i = 0; i < LUT_SIZE; i++) {
            double scale = ColorSpaces.luminanceFromLightness(100.0 * i / (LUT_SIZE - 1)) / baseY;
            lut[i] = ColorSpaces.linearToRgb(base[0] * scale, base[1] * scale, base[2] * scale);
        }
        return lut;
    }

    /** Identidad hasta la rodilla y, desde ahí, aproximación suave a {@code cap}. */
    private static double soft(double x, double cap) {
        double knee = KNEE_RATIO * cap;
        if (x <= knee) {
            return x;
        }
        return knee + (cap - knee) * Math.tanh((x - knee) / (cap - knee));
    }

    private static int bin(double l) {
        return Math.max(0, Math.min(HISTOGRAM_BINS - 1, (int) Math.round(l / HISTOGRAM_STEP)));
    }
}
