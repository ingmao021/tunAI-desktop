package com.tunai.desktop.paint;

/** Conversiones sRGB ↔ luz lineal y sRGB → CIE Lab (D65), y mezcla en luz lineal. */
final class ColorSpaces {

    private static final double[] LINEAR = new double[256];
    /** sRGB codificado (0–255) para luz lineal de 0 a 1 en pasos de 1/ENCODE_STEPS. */
    private static final int ENCODE_STEPS = 16384;
    private static final int[] ENCODE = new int[ENCODE_STEPS + 1];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            LINEAR[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
        for (int i = 0; i <= ENCODE_STEPS; i++) {
            double c = (double) i / ENCODE_STEPS;
            double s = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
            ENCODE[i] = (int) Math.round(s * 255);
        }
    }

    private ColorSpaces() {
    }

    /** @return {L, a, b} */
    static double[] rgbToLab(int rgb, double[] out) {
        double r = LINEAR[(rgb >> 16) & 0xFF];
        double g = LINEAR[(rgb >> 8) & 0xFF];
        double b = LINEAR[rgb & 0xFF];
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = luminance(r, g, b);
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x);
        double fy = f(y);
        double fz = f(z);
        out[0] = 116 * fy - 16;
        out[1] = 500 * (fx - fy);
        out[2] = 200 * (fy - fz);
        return out;
    }

    /** Solo la luminosidad L* de Lab (0–100), sin calcular a y b. */
    static double lightness(int rgb) {
        return lightnessFromLuminance(luminance(LINEAR[(rgb >> 16) & 0xFF], LINEAR[(rgb >> 8) & 0xFF], LINEAR[rgb & 0xFF]));
    }

    /** L* (0–100) que corresponde a una luminancia relativa Y (0–1). */
    static double lightnessFromLuminance(double y) {
        return 116 * f(y) - 16;
    }

    /** Luminancia relativa Y (0–1) de un color en luz lineal. */
    static double luminance(double r, double g, double b) {
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /** Inversa de L*: luminancia relativa Y (0–1) que corresponde a una luminosidad de 0 a 100. */
    static double luminanceFromLightness(double l) {
        if (l <= 8) {
            return l / 903.3;
        }
        double t = (l + 16) / 116;
        return t * t * t;
    }

    /** Luz lineal (0–1) de cada canal de un color {@code 0xRRGGBB}. */
    static double[] linearRgb(int rgb, double[] out) {
        out[0] = LINEAR[(rgb >> 16) & 0xFF];
        out[1] = LINEAR[(rgb >> 8) & 0xFF];
        out[2] = LINEAR[rgb & 0xFF];
        return out;
    }

    /** Luz lineal → RGB empaquetado {@code 0xRRGGBB}; lo que sale de 0–1 se recorta. */
    static int linearToRgb(double r, double g, double b) {
        return (encode(r) << 16) | (encode(g) << 8) | encode(b);
    }

    /** Mezcla de dos colores {@code 0xRRGGBB} en luz lineal (t=0 → a, t=1 → b). */
    static int mixLinear(int a, int b, double t) {
        int r = encode(LINEAR[(a >> 16) & 0xFF] * (1 - t) + LINEAR[(b >> 16) & 0xFF] * t);
        int g = encode(LINEAR[(a >> 8) & 0xFF] * (1 - t) + LINEAR[(b >> 8) & 0xFF] * t);
        int bl = encode(LINEAR[a & 0xFF] * (1 - t) + LINEAR[b & 0xFF] * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int encode(double linear) {
        double c = Math.max(0, Math.min(1, linear));
        return ENCODE[(int) (c * ENCODE_STEPS + 0.5)];
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
    }
}
