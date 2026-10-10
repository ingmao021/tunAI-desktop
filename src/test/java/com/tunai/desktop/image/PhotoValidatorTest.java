package com.tunai.desktop.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CA-UI-04: una foto inválida se rechaza sin llamar al backend. */
class PhotoValidatorTest {

    @TempDir
    Path dir;

    static byte[] encode(int w, int h, String format) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private Path write(String name, byte[] content) throws Exception {
        Path p = dir.resolve(name);
        Files.write(p, content);
        return p;
    }

    @Test
    void acceptsValidJpegAndPng() throws Exception {
        Photo jpg = PhotoValidator.validate(write("carro.jpg", encode(800, 600, "jpg")));
        assertEquals(Photo.Format.JPEG, jpg.format());
        assertEquals(800, jpg.width());
        assertEquals(600, jpg.height());

        Photo png = PhotoValidator.validate(write("carro.png", encode(512, 512, "png")));
        assertEquals(Photo.Format.PNG, png.format());
        assertEquals("image/png", png.format().mimeType());
    }

    @Test
    void formatComesFromBytesNotExtension() throws Exception {
        // PNG con extensión .jpg: se acepta como PNG.
        Photo p = PhotoValidator.validate(write("enganoso.jpg", encode(600, 600, "png")));
        assertEquals(Photo.Format.PNG, p.format());

        // GIF con extensión .png: se rechaza.
        Path gif = write("foto.png", encode(600, 600, "gif"));
        var e = assertThrows(PhotoValidator.InvalidPhotoException.class, () -> PhotoValidator.validate(gif));
        assertEquals(PhotoValidator.ERR_FORMAT, e.getMessage());
    }

    @Test
    void rejectsSmallImages() throws Exception {
        Path small = write("pequena.jpg", encode(1024, 511, "jpg"));
        var e = assertThrows(PhotoValidator.InvalidPhotoException.class, () -> PhotoValidator.validate(small));
        assertEquals(PhotoValidator.ERR_DIMENSIONS, e.getMessage());
    }

    @Test
    void rejectsLargeFilesBeforeDecoding() throws Exception {
        byte[] big = new byte[(int) PhotoValidator.MAX_BYTES + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        Path p = write("enorme.jpg", big);
        var e = assertThrows(PhotoValidator.InvalidPhotoException.class, () -> PhotoValidator.validate(p));
        assertEquals(PhotoValidator.ERR_SIZE, e.getMessage());
    }

    @Test
    void rejectsTruncatedJpeg() throws Exception {
        Path p = write("rota.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0});
        var e = assertThrows(PhotoValidator.InvalidPhotoException.class, () -> PhotoValidator.validate(p));
        assertEquals(PhotoValidator.ERR_UNREADABLE, e.getMessage());
    }
}
