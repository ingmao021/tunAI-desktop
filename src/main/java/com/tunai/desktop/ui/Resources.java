package com.tunai.desktop.ui;

import javafx.scene.image.Image;
import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

public final class Resources {

    public static final String STYLESHEET = "/com/tunai/desktop/styles/app.css";
    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final String[] FONTS = {"Rajdhani-Bold.ttf"};

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

    /** Registra las fuentes propias (familia "Rajdhani" en app.css). Sin ellas la UI cae a la del sistema. */
    public static void loadFonts() {
        for (String font : FONTS) {
            try (InputStream in = Resources.class.getResourceAsStream("/com/tunai/desktop/fonts/" + font)) {
                if (in != null) {
                    Font.loadFont(in, 14);
                }
            } catch (IOException e) {
                // Solo es estética: se sigue con la fuente del sistema.
            }
        }
    }

    public static String stylesheet() {
        return Resources.class.getResource(STYLESHEET).toExternalForm();
    }

    /** Icono de la app en varios tamaños; Windows elige el que mejor encaje (barra de título, tareas, Alt+Tab). */
    public static List<Image> appIcons() {
        return IntStream.of(ICON_SIZES)
                .mapToObj(size -> new Image(Resources.class.getResource("/com/tunai/desktop/icons/icon-" + size + ".png")
                        .toExternalForm()))
                .toList();
    }
}
