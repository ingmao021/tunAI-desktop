package com.tunai.desktop.image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Validación local antes de enviar (§5.3), la misma que hace el backend, para no gastar una petición.
 * El formato se decide por la firma de bytes, nunca por la extensión.
 */
public final class PhotoValidator {

    /** 10 MB. Se usa la definición decimal, la más estricta, para no gastar una petición. */
    public static final long MAX_BYTES = 10_000_000L;
    public static final int MIN_SIDE = 512;

    public static final String ERR_FORMAT = "Solo se aceptan fotos JPG o PNG.";
    public static final String ERR_SIZE = "La foto pesa más de 10 MB. Usa una de menor tamaño.";
    public static final String ERR_DIMENSIONS = "La foto es muy pequeña. Necesita al menos 512 px por lado.";
    public static final String ERR_UNREADABLE = "No pudimos leer la foto. Prueba con otra.";

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};

    private PhotoValidator() {
    }

    public static Photo validate(Path file) throws InvalidPhotoException {
        Photo.Format format;
        long size;
        try {
            size = Files.size(file);
            try (InputStream in = Files.newInputStream(file)) {
                format = detectFormat(in.readNBytes(4));
            }
        } catch (IOException e) {
            throw new InvalidPhotoException(ERR_UNREADABLE);
        }
        if (format == null) {
            throw new InvalidPhotoException(ERR_FORMAT);
        }
        if (size > MAX_BYTES) {
            throw new InvalidPhotoException(ERR_SIZE);
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new InvalidPhotoException(ERR_UNREADABLE);
        }
        int[] dims = dimensions(bytes);
        if (dims == null) {
            throw new InvalidPhotoException(ERR_UNREADABLE);
        }
        if (Math.min(dims[0], dims[1]) < MIN_SIDE) {
            throw new InvalidPhotoException(ERR_DIMENSIONS);
        }
        return new Photo(file.getFileName().toString(), bytes, format, dims[0], dims[1]);
    }

    static Photo.Format detectFormat(byte[] head) {
        if (startsWith(head, JPEG_MAGIC)) {
            return Photo.Format.JPEG;
        }
        if (startsWith(head, PNG_MAGIC)) {
            return Photo.Format.PNG;
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Lee ancho y alto desde la cabecera, sin decodificar los píxeles. */
    private static int[] dimensions(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

    public static class InvalidPhotoException extends Exception {
        public InvalidPhotoException(String userMessage) {
            super(userMessage);
        }
    }
}
