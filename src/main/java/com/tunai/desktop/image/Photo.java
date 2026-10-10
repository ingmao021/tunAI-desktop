package com.tunai.desktop.image;

/** Foto que pasó la validación local, lista para clasificar y enviar. */
public record Photo(String fileName, byte[] bytes, Format format, int width, int height) {

    public enum Format {
        JPEG("image/jpeg"),
        PNG("image/png");

        private final String mimeType;

        Format(String mimeType) {
            this.mimeType = mimeType;
        }

        public String mimeType() {
            return mimeType;
        }
    }
}
