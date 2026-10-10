package com.tunai.desktop.paint;

/**
 * Decide qué píxeles de la textura son pintura (§5.5). Es la parte de mayor riesgo: queda detrás
 * de esta interfaz para poder cambiar el enfoque tras el ensayo con el GLB real.
 */
public interface PaintMaskStrategy {

    /**
     * @param argb     píxeles de la textura original, fila por fila
     * @param coverage píxeles que usa el modelo (islas UV); {@code null} si se desconoce
     * @return peso de 0 a 1 por píxel: cuánto pertenece a la pintura
     */
    float[] computeMask(int[] argb, int width, int height, boolean[] coverage);
}
