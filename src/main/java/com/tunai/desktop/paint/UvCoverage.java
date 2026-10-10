package com.tunai.desktop.paint;

/** Rasteriza los triángulos UV para saber qué píxeles de la textura usa el modelo. */
public final class UvCoverage {

    private UvCoverage() {
    }

    /**
     * @param texCoords pares (u, v) por vértice, origen arriba a la izquierda (igual en glTF y JavaFX)
     * @param indices   tres índices por triángulo
     */
    public static boolean[] compute(float[] texCoords, int[] indices, int width, int height) {
        return compute(texCoords, indices, null, width, height);
    }

    /**
     * Igual que {@link #compute(float[], int[], int, int)}, pero omite los triángulos que miran hacia
     * abajo (bajos del carro). SF3D rellena con gris lo que la foto no ve, y esas zonas no son pintura.
     *
     * @param normals normales por vértice en ejes de JavaFX (Y hacia abajo); {@code null} no filtra
     */
    public static boolean[] compute(float[] texCoords, int[] indices, float[] normals, int width, int height) {
        boolean[] covered = new boolean[width * height];
        for (int t = 0; t + 2 < indices.length; t += 3) {
            if (normals != null && facesDown(normals, indices[t], indices[t + 1], indices[t + 2])) {
                continue;
            }
            double x0 = wrap(texCoords[indices[t] * 2]) * width;
            double y0 = wrap(texCoords[indices[t] * 2 + 1]) * height;
            double x1 = wrap(texCoords[indices[t + 1] * 2]) * width;
            double y1 = wrap(texCoords[indices[t + 1] * 2 + 1]) * height;
            double x2 = wrap(texCoords[indices[t + 2] * 2]) * width;
            double y2 = wrap(texCoords[indices[t + 2] * 2 + 1]) * height;
            fill(covered, width, height, x0, y0, x1, y1, x2, y2);
        }
        return covered;
    }

    /** Normal media con componente Y (hacia abajo en JavaFX) mayor que {@link #DOWN_THRESHOLD}. */
    private static boolean facesDown(float[] n, int a, int b, int c) {
        double x = n[a * 3] + n[b * 3] + n[c * 3];
        double y = n[a * 3 + 1] + n[b * 3 + 1] + n[c * 3 + 1];
        double z = n[a * 3 + 2] + n[b * 3 + 2] + n[c * 3 + 2];
        double len = Math.sqrt(x * x + y * y + z * z);
        return len > 1e-9 && y / len > DOWN_THRESHOLD;
    }

    static final double DOWN_THRESHOLD = 0.5;

    private static double wrap(float v) {
        return v >= 0 && v <= 1 ? v : v - Math.floor(v);
    }

    /** Relleno por baricéntricas con un píxel de margen para cubrir los bordes de las islas. */
    private static void fill(boolean[] out, int w, int h,
                             double x0, double y0, double x1, double y1, double x2, double y2) {
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))) - 1);
        int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))) + 1);
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))) - 1);
        int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))) + 1);
        double area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (Math.abs(area) < 1e-12) {
            return;
        }
        double tolerance = 1.0 / Math.max(1, Math.sqrt(Math.abs(area)));
        for (int y = minY; y <= maxY; y++) {
            double py = y + 0.5;
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5;
                double w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area;
                double w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 >= -tolerance && w1 >= -tolerance && w2 >= -tolerance) {
                    out[y * w + x] = true;
                }
            }
        }
    }
}
