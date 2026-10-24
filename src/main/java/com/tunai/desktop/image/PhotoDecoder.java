package com.tunai.desktop.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Decodifica la foto aplicando la orientación EXIF de los JPEG de celular, para que la vista previa
 * y el clasificador vean el carro derecho. Lo que se envía al backend son los bytes originales.
 */
public final class PhotoDecoder {

    private PhotoDecoder() {
    }

    public static BufferedImage decode(Photo photo) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(photo.bytes()));
        if (image == null) {
            throw new IOException("Formato de imagen no soportado");
        }
        int orientation = photo.format() == Photo.Format.JPEG ? exifOrientation(photo.bytes()) : 1;
        return orientation > 1 ? applyOrientation(image, orientation) : image;
    }

    /** Valor de la etiqueta EXIF 0x0112 (1–8); 1 si no hay. */
    static int exifOrientation(byte[] jpeg) {
        int pos = 2;
        while (pos + 4 <= jpeg.length) {
            if ((jpeg[pos] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                return 1;
            }
            int length = u16(jpeg, pos + 2, false);
            int start = pos + 4;
            if (marker == 0xE1 && start + 6 <= jpeg.length
                    && jpeg[start] == 'E' && jpeg[start + 1] == 'x' && jpeg[start + 2] == 'i' && jpeg[start + 3] == 'f') {
                return readOrientation(jpeg, start + 6, Math.min(jpeg.length, pos + 2 + length));
            }
            pos += 2 + length;
        }
        return 1;
    }

    private static int readOrientation(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) {
            return 1;
        }
        boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        int ifd = tiff + (int) u32(b, tiff + 4, little);
        if (ifd + 2 > end) {
            return 1;
        }
        int entries = u16(b, ifd, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (u16(b, entry, little) == 0x0112) {
                int value = u16(b, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int i, boolean little) {
        return little ? (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8 : (b[i] & 0xFF) << 8 | (b[i + 1] & 0xFF);
    }

    private static long u32(byte[] b, int i, boolean little) {
        long v = little
                ? (b[i] & 0xFFL) | (b[i + 1] & 0xFFL) << 8 | (b[i + 2] & 0xFFL) << 16 | (b[i + 3] & 0xFFL) << 24
                : (b[i] & 0xFFL) << 24 | (b[i + 1] & 0xFFL) << 16 | (b[i + 2] & 0xFFL) << 8 | (b[i + 3] & 0xFFL);
        return v;
    }

    /** Transforma la imagen guardada a como debe mostrarse, según la orientación EXIF. */
    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        int ow = swap ? h : w;
        int oh = swap ? w : h;
        int[] in = src.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[in.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx;
                int dy;
                switch (orientation) {
                    case 2 -> { dx = w - 1 - x; dy = y; }
                    case 3 -> { dx = w - 1 - x; dy = h - 1 - y; }
                    case 4 -> { dx = x; dy = h - 1 - y; }
                    case 5 -> { dx = y; dy = x; }
                    case 6 -> { dx = h - 1 - y; dy = x; }
                    case 7 -> { dx = h - 1 - y; dy = w - 1 - x; }
                    case 8 -> { dx = y; dy = w - 1 - x; }
                    default -> { dx = x; dy = y; }
                }
                out[dy * ow + dx] = in[y * w + x];
            }
        }
        BufferedImage result = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
        result.setRGB(0, 0, ow, oh, out, 0, ow);
        return result;
    }
}
