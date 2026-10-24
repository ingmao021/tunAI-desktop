package com.tunai.desktop;

/**
 * Punto de entrada del jar. No extiende {@code Application} para que la app arranque
 * con JavaFX en el classpath (jar empaquetado con jpackage, sin module-path).
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
