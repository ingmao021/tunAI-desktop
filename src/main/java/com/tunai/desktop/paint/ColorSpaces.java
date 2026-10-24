package com.tunai.desktop.paint;

/** Conversiones sRGB ↔ HSL y sRGB → CIE Lab (D65). */
final class ColorSpaces {

    private static final double[] LINEAR = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            LINEAR[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
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
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x);
        double fy = f(y);
        double fz = f(z);
        out[0] = 116 * fy - 16;
        out[1] = 500 * (fx - fy);
        out[2] = 200 * (fy - fz);
        return out;
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
    }

    /** @return {h (0–360), s (0–1), l (0–1)} */
    static double[] rgbToHsl(int rgb, double[] out) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2;
        double h = 0;
        double s = 0;
        double d = max - min;
        if (d > 1e-9) {
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) {
                h = ((g - b) / d + (g < b ? 6 : 0)) * 60;
            } else if (max == g) {
                h = ((b - r) / d + 2) * 60;
            } else {
                h = ((r - g) / d + 4) * 60;
            }
        }
        out[0] = h;
        out[1] = s;
        out[2] = l;
        return out;
    }

    /** @return RGB empaquetado {@code 0xRRGGBB} */
    static int hslToRgb(double h, double s, double l) {
        double r;
        double g;
        double b;
        if (s <= 1e-9) {
            r = g = b = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            double hk = h / 360.0;
            r = hue(p, q, hk + 1.0 / 3);
            g = hue(p, q, hk);
            b = hue(p, q, hk - 1.0 / 3);
        }
        return (to255(r) << 16) | (to255(g) << 8) | to255(b);
    }

    private static double hue(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1.0 / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 0.5) {
            return q;
        }
        if (t < 2.0 / 3) {
            return p + (q - p) * (2.0 / 3 - t) * 6;
        }
        return p;
    }

    private static int to255(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }
}
