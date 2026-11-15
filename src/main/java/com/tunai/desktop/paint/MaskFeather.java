package com.tunai.desktop.paint;

/**
 * Suaviza los bordes de la máscara de pintura para que el cambio de color no deje escalones ni flecos
 * claros donde la pintura termina.
 *
 * <p>Es un desenfoque binomial separable de 5 píxeles, "normalizado": cada píxel promedia solo con sus
 * vecinos permitidos (los que usa el modelo). Así el borde de una isla UV no se aclara por mezclarse con el
 * vacío del atlas, que de otro modo dibujaría costuras en el modelo.</p>
 */
final class MaskFeather {

    private static final int[] KERNEL = {1, 4, 6, 4, 1};
    private static final int RADIUS = 2;

    private MaskFeather() {
    }

    /**
     * @param allowed píxeles que pueden contribuir y recibir pintura; los demás conservan su valor
     * @return máscara nueva con valores de 0 a 1
     */
    static float[] feather(float[] mask, boolean[] allowed, int width, int height) {
        int n = width * height;
        float[] sum = new float[n];
        byte[] weight = new byte[n];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                float s = 0;
                int wsum = 0;
                for (int k = -RADIUS; k <= RADIUS; k++) {
                    int xx = x + k;
                    if (xx < 0 || xx >= width || !allowed[row + xx]) {
                        continue;
                    }
                    s += KERNEL[k + RADIUS] * mask[row + xx];
                    wsum += KERNEL[k + RADIUS];
                }
                sum[row + x] = s;
                weight[row + x] = (byte) wsum;
            }
        }
        float[] out = new float[n];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (!allowed[i]) {
                    out[i] = mask[i];
                    continue;
                }
                float s = 0;
                int wsum = 0;
                for (int k = -RADIUS; k <= RADIUS; k++) {
                    int yy = y + k;
                    if (yy < 0 || yy >= height) {
                        continue;
                    }
                    int j = yy * width + x;
                    s += KERNEL[k + RADIUS] * sum[j];
                    wsum += KERNEL[k + RADIUS] * weight[j];
                }
                out[i] = wsum > 0 ? Math.max(0f, Math.min(1f, s / wsum)) : mask[i];
            }
        }
        return out;
    }
}
