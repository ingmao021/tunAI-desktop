package com.tunai.desktop.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class Resources {

    public static final String STYLESHEET = "/com/tunai/desktop/styles/app.css";

    private Resources() {
    }

    public static String text(String path) {
        try (InputStream in = Resources.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Recurso no encontrado: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String stylesheet() {
        return Resources.class.getResource(STYLESHEET).toExternalForm();
    }
}
