package com.tunai.desktop.paint;

/**
 * Máscara de pintura ajustada con un GLB real de SF3D (Renault Duster plateado).
 *
 * <p>En SF3D la pintura que se ve en la foto ocupa una parte minoritaria de la textura: las caras que
 * la foto no muestra (techo, el otro lado) se rellenan con grises medios. Por eso "el color más
 * común" no sirve. En su lugar:</p>
 * <ul>
 *   <li><b>Pintura con color</b> (hay suficientes píxeles con croma): se elige el tono dominante entre
 *       los píxeles con color y se marca por cercanía de tono, sin importar la luminosidad (las zonas
 *       más oscuras de la misma pintura también cuentan).</li>
 *   <li><b>Pintura acromática</b> (blanco, plata, gris): se separa por luminosidad con un umbral de
 *       Otsu entre la carrocería clara y los vidrios, llantas y plásticos oscuros.</li>
 * </ul>
 * Solo se consideran los píxeles que usa el modelo ({@code coverage}).
 */
public class BodyPaintMaskStrategy implements PaintMaskStrategy {

    /** Croma Lab desde el que un píxel cuenta como "con color". */
    static final double CHROMA_MIN = 14;
    /** Fracción mínima de píxeles con color para tratar la pintura como cromática. */
    static final double CHROMATIC_SHARE = 0.25;
    /** Diferencia de tono (grados) con peso completo / con peso cero. */
    static final double HUE_FULL = 18;
    static final double HUE_ZERO = 40;
    /** Píxeles muy oscuros (llantas, sombras) nunca son pintura con color. */
    static final double CHROMATIC_MIN_L = 18;
    /** Rango de búsqueda del umbral de Otsu y ancho de la rampa suave alrededor. */
    static final int OTSU_MIN = 30;
    static final int OTSU_MAX = 80;
    static final double L_RAMP = 7;

    @Override
    public float[] computeMask(int[] argb, int width, int height, boolean[] coverage) {
        int n = argb.length;
        float[] lightness = new float[n];
        float[] chroma = new float[n];
        float[] hue = new float[n];
        double[] lab = new double[3];
        int counted = 0;
        int chromatic = 0;
        double[] hueHistogram = new double[36];
        for (int i = 0; i < n; i++) {
            if (!usable(argb[i], coverage, i)) {
                continue;
            }
            ColorSpaces.rgbToLab(argb[i], lab);
            lightness[i] = (float) lab[0];
            chroma[i] = (float) Math.hypot(lab[1], lab[2]);
            hue[i] = (float) ((Math.toDegrees(Math.atan2(lab[2], lab[1])) + 360) % 360);
            if (lab[0] > CHROMATIC_MIN_L) {
                counted++;
                if (chroma[i] >= CHROMA_MIN) {
                    chromatic++;
                    hueHistogram[(int) (hue[i] / 10) % 36] += chroma[i];
                }
            }
        }
        float[] mask = new float[n];
        if (counted == 0) {
            return mask;
        }
        if (chromatic >= CHROMATIC_SHARE * counted) {
            double paintHue = dominantHue(hueHistogram, hue, chroma, argb, coverage);
            for (int i = 0; i < n; i++) {
                if (!usable(argb[i], coverage, i) || lightness[i] <= CHROMATIC_MIN_L) {
                    continue;
                }
                double dh = Math.abs(hue[i] - paintHue);
                dh = Math.min(dh, 360 - dh);
                double hueWeight = 1 - smoothstep(HUE_FULL, HUE_ZERO, dh);
                double chromaWeight = smoothstep(CHROMA_MIN * 0.5, CHROMA_MIN * 1.5, chroma[i]);
                mask[i] = (float) (hueWeight * chromaWeight);
            }
        } else {
            double threshold = otsu(lightness, chroma, argb, coverage);
            for (int i = 0; i < n; i++) {
                if (!usable(argb[i], coverage, i)) {
                    continue;
                }
                double lightWeight = smoothstep(threshold - L_RAMP, threshold + L_RAMP, lightness[i]);
                double neutralWeight = 1 - smoothstep(CHROMA_MIN, CHROMA_MIN * 2, chroma[i]);
                mask[i] = (float) (lightWeight * neutralWeight);
            }
        }
        return mask;
    }

    /** Pico del histograma de tono (suavizado con los vecinos) refinado con la media circular. */
    private static double dominantHue(double[] histogram, float[] hue, float[] chroma, int[] argb, boolean[] coverage) {
        int best = 0;
        double bestScore = -1;
        for (int b = 0; b < 36; b++) {
            double score = histogram[(b + 35) % 36] + histogram[b] + histogram[(b + 1) % 36];
            if (score > bestScore) {
                bestScore = score;
                best = b;
            }
        }
        double center = best * 10 + 5;
        double sx = 0;
        double sy = 0;
        for (int i = 0; i < hue.length; i++) {
            if (!usable(argb[i], coverage, i) || chroma[i] < CHROMA_MIN) {
                continue;
            }
            double dh = Math.abs(hue[i] - center);
            if (Math.min(dh, 360 - dh) <= 20) {
                sx += Math.cos(Math.toRadians(hue[i])) * chroma[i];
                sy += Math.sin(Math.toRadians(hue[i])) * chroma[i];
            }
        }
        return (Math.toDegrees(Math.atan2(sy, sx)) + 360) % 360;
    }

    /** Umbral de Otsu sobre la luminosidad de los píxeles neutros, limitado a [30, 80]. */
    static double otsu(float[] lightness, float[] chroma, int[] argb, boolean[] coverage) {
        double[] hist = new double[101];
        double total = 0;
        for (int i = 0; i < lightness.length; i++) {
            if (usable(argb[i], coverage, i) && chroma[i] < CHROMA_MIN * 2) {
                hist[Math.max(0, Math.min(100, Math.round(lightness[i])))]++;
                total++;
            }
        }
        if (total == 0) {
            return 50;
        }
        double sumAll = 0;
        for (int l = 0; l <= 100; l++) {
            sumAll += l * hist[l];
        }
        double weightBelow = 0;
        double sumBelow = 0;
        double bestVariance = -1;
        int first = 50;
        int last = 50;
        for (int t = 0; t < 100; t++) {
            weightBelow += hist[t];
            sumBelow += t * hist[t];
            double weightAbove = total - weightBelow;
            if (weightBelow == 0 || weightAbove == 0 || t < OTSU_MIN || t > OTSU_MAX) {
                continue;
            }
            double meanBelow = sumBelow / weightBelow;
            double meanAbove = (sumAll - sumBelow) / weightAbove;
            double variance = weightBelow * weightAbove * (meanBelow - meanAbove) * (meanBelow - meanAbove);
            if (variance > bestVariance * (1 + 1e-9)) {
                bestVariance = variance;
                first = t;
                last = t;
            } else if (variance >= bestVariance * (1 - 1e-9)) {
                last = t;
            }
        }
        // Si hay un hueco entre los dos grupos, todos sus umbrales empatan: se toma el centro.
        return (first + last) / 2.0 + 0.5;
    }

    private static boolean usable(int argb, boolean[] coverage, int i) {
        return (coverage == null || coverage[i]) && (argb >>> 24) >= 128;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }
}
