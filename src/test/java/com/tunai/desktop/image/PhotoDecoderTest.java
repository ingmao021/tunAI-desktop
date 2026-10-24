package com.tunai.desktop.image;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhotoDecoderTest {

    private static final int RED = 0xFF0000;

    /** Imagen 3×2 con el píxel superior izquierdo rojo. */
    private static BufferedImage marked() {
        BufferedImage img = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, RED);
        return img;
    }

    @ParameterizedTest(name = "orientación {0}")
    @CsvSource({
            "1, 3, 2, 0, 0",
            "2, 3, 2, 2, 0",
            "3, 3, 2, 2, 1",
            "4, 3, 2, 0, 1",
            "5, 2, 3, 0, 0",
            "6, 2, 3, 1, 0",
            "7, 2, 3, 1, 2",
            "8, 2, 3, 0, 2",
    })
    void topLeftPixelEndsInExpectedCorner(int orientation, int w, int h, int x, int y) {
        BufferedImage out = PhotoDecoder.applyOrientation(marked(), orientation);
        assertEquals(w, out.getWidth());
        assertEquals(h, out.getHeight());
        assertEquals(RED, out.getRGB(x, y) & 0xFFFFFF);
    }

    @Test
    void readsOrientationFromExif() {
        assertEquals(6, PhotoDecoder.exifOrientation(jpegWithOrientation(6, false)));
        assertEquals(8, PhotoDecoder.exifOrientation(jpegWithOrientation(8, true)));
        assertEquals(1, PhotoDecoder.exifOrientation(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9}));
    }

    /** SOI + APP1 Exif con un IFD de una entrada (0x0112) + EOI. */
    static byte[] jpegWithOrientation(int orientation, boolean littleEndian) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        if (littleEndian) {
            tiff.writeBytes(new byte[]{'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0,
                    (byte) orientation, 0, 0, 0, 0, 0, 0, 0});
        } else {
            tiff.writeBytes(new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1,
                    0, (byte) orientation, 0, 0, 0, 0, 0, 0});
        }
        byte[] payload = tiff.toByteArray();
        int length = 2 + 6 + payload.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
        out.writeBytes(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        out.writeBytes(payload);
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xD9});
        return out.toByteArray();
    }
}
