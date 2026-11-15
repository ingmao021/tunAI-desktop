package com.tunai.desktop.paint;

/**
 * Pasos que se aplican a la máscara de color ({@link PaintMaskStrategy}) usando la geometría del modelo:
 * primero se quita la pintura de las piezas que no son carrocería (rines) y después se suavizan los bordes.
 */
public final class PaintMaskRefiner {

    private PaintMaskRefiner() {
    }

    /**
     * @param mask      máscara de color por píxel
     * @param coverage  píxeles que usa el modelo; {@code null} si se desconoce
     * @param points    posiciones por vértice (x, y, z)
     * @param texCoords coordenadas UV por vértice
     * @param indices   tres índices por triángulo
     * @return máscara refinada; la original no se modifica
     */
    public static float[] refine(float[] mask, boolean[] coverage, int width, int height,
                                 float[] points, float[] texCoords, int[] indices) {
        float[] out = mask.clone();
        boolean[] allowed = new boolean[mask.length];
        boolean[] dropped = PaintIslandFilter.droppedPixels(mask, width, height, points, texCoords, indices);
        for (int i = 0; i < out.length; i++) {
            boolean drop = dropped != null && dropped[i];
            if (drop) {
                out[i] = 0f;
            }
            allowed[i] = !drop && (coverage == null || coverage[i]);
        }
        return MaskFeather.feather(out, allowed, width, height);
    }
}
