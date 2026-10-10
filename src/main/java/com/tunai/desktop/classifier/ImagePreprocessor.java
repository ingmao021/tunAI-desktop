package com.tunai.desktop.classifier;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Preprocesado de MobileNetV2 (§5.3.1): 224×224, RGB, normalización de ImageNet y orden NCHW.
 */
public final class ImagePreprocessor {

    public static final int SIZE = 224;
    static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    static final float[] STD = {0.229f, 0.224f, 0.225f};

    private ImagePreprocessor() {
    }

    /** @return tensor plano {@code [1, 3, 224, 224]} */
    public static float[] toTensor(BufferedImage image) {
        BufferedImage resized = resize(image, SIZE, SIZE);
        int plane = SIZE * SIZE;
        float[] data = new float[3 * plane];
        int[] rgb = resized.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
        for (int i = 0; i < plane; i++) {
            int p = rgb[i];
            data[i] = (((p >> 16) & 0xFF) / 255f - MEAN[0]) / STD[0];
            data[plane + i] = (((p >> 8) & 0xFF) / 255f - MEAN[1]) / STD[1];
            data[2 * plane + i] = ((p & 0xFF) / 255f - MEAN[2]) / STD[2];
        }
        return data;
    }

    /**
     * Redimensiona a la mitad en varios pasos bilineales y luego al tamaño final, para no
     * perder detalle (aliasing) al reducir fotos grandes de cámara.
     */
    static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage current = toRgb(source);
        int w = current.getWidth();
        int h = current.getHeight();
        while (w / 2 >= width && h / 2 >= height) {
            w /= 2;
            h /= 2;
            current = draw(current, w, h);
        }
        return draw(current, width, height);
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        // Las zonas transparentes de un PNG se rellenan de blanco.
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static BufferedImage draw(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return out;
    }
}
