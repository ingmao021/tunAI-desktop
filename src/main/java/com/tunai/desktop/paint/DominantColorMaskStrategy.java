package com.tunai.desktop.paint;

/**
 * Enfoque inicial (§5.5): toma el color dominante de la textura y marca los píxeles cercanos a él.
 * <ul>
 *   <li>Solo cuenta los píxeles que usa el modelo (islas UV), para que el fondo vacío del atlas
 *       no gane.</li>
 *   <li>El color dominante sale de un histograma en Lab (luminosidad y croma cuantizados).</li>
 *   <li>El peso cae suavemente con la distancia en Lab, dando menos importancia a la luminosidad
 *       para tolerar reflejos y zonas más oscuras de la misma pintura.</li>
 * </ul>
 * Los umbrales se ajustan con el GLB del ensayo real.
 */
public class DominantColorMaskStrategy implements PaintMaskStrategy {

    /** Distancia Lab hasta la que el píxel es pintura del todo. */
    private final double fullDistance;
    /** Distancia Lab desde la que el píxel ya no es pintura. */
    private final double zeroDistance;
    private final double lightnessWeight;

    public DominantColorMaskStrategy() {
        this(18, 34, 0.45);
    }

    public DominantColorMaskStrategy(double fullDistance, double zeroDistance, double lightnessWeight) {
        this.fullDistance = fullDistance;
        this.zeroDistance = zeroDistance;
        this.lightnessWeight = lightnessWeight;
    }

    @Override
    public float[] computeMask(int[] argb, int width, int height, boolean[] coverage) {
        double[] dominant = dominantLab(argb, coverage);
        float[] mask = new float[argb.length];
        if (dominant == null) {
            return mask;
        }
        double[] lab = new double[3];
        for (int i = 0; i < argb.length; i++) {
            if ((coverage != null && !coverage[i]) || (argb[i] >>> 24) < 128) {
                continue;
            }
            ColorSpaces.rgbToLab(argb[i], lab);
            double dl = (lab[0] - dominant[0]) * lightnessWeight;
            double da = lab[1] - dominant[1];
            double db = lab[2] - dominant[2];
            double d = Math.sqrt(dl * dl + da * da + db * db);
            mask[i] = (float) (1 - smoothstep(fullDistance, zeroDistance, d));
        }
        return mask;
    }

    /** Media Lab de la celda más poblada del histograma (L en pasos de 10, a/b en pasos de 12). */
    double[] dominantLab(int[] argb, boolean[] coverage) {
        final int lBins = 11;
        final int abBins = 22;
        final double abMin = -132;
        int[] counts = new int[lBins * abBins * abBins];
        double[][] sums = new double[counts.length][];
        double[] lab = new double[3];
        for (int i = 0; i < argb.length; i++) {
            if ((coverage != null && !coverage[i]) || (argb[i] >>> 24) < 128) {
                continue;
            }
            ColorSpaces.rgbToLab(argb[i], lab);
            int li = clampBin((int) (lab[0] / 10), lBins);
            int ai = clampBin((int) ((lab[1] - abMin) / 12), abBins);
            int bi = clampBin((int) ((lab[2] - abMin) / 12), abBins);
            int cell = (li * abBins + ai) * abBins + bi;
            counts[cell]++;
            if (sums[cell] == null) {
                sums[cell] = new double[3];
            }
            sums[cell][0] += lab[0];
            sums[cell][1] += lab[1];
            sums[cell][2] += lab[2];
        }
        int best = -1;
        for (int c = 0; c < counts.length; c++) {
            if (counts[c] > 0 && (best < 0 || counts[c] > counts[best])) {
                best = c;
            }
        }
        if (best < 0) {
            return null;
        }
        return new double[]{sums[best][0] / counts[best], sums[best][1] / counts[best], sums[best][2] / counts[best]};
    }

    private static int clampBin(int v, int bins) {
        return Math.max(0, Math.min(bins - 1, v));
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }
}
