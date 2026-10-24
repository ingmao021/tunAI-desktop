package com.tunai.desktop.image;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Conversiones entre imágenes de JavaFX, píxeles ARGB y PNG. */
public final class FxImages {

    public static final int THUMBNAIL_SIZE = 256;

    private FxImages() {
    }

    public static int[] argb(Image image) {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] px = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        return px;
    }

    public static WritableImage fromArgb(int[] px, int w, int h) {
        WritableImage img = new WritableImage(w, h);
        img.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        return img;
    }

    public static byte[] png(Image image) throws IOException {
        return png(SwingFXUtils.fromFXImage(image, null));
    }

    public static byte[] png(int[] argb, int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        return png(img);
    }

    /** Miniatura cuadrada de 256×256 recortada del centro de la captura (§4). */
    public static byte[] thumbnailPng(Image snapshot) throws IOException {
        BufferedImage src = SwingFXUtils.fromFXImage(snapshot, null);
        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2;
        int y = (src.getHeight() - side) / 2;
        BufferedImage out = new BufferedImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE, x, y, x + side, y + side, null);
        g.dispose();
        return png(out);
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "png", out)) {
            throw new IOException("No hay escritor PNG");
        }
        return out.toByteArray();
    }
}
