package com.tunai.desktop.config;

import java.nio.file.Path;

/**
 * Rutas de los datos locales (§4). Todo vive en {@code %APPDATA%\TunAI\}.
 * La propiedad de sistema {@code tunai.home} permite cambiar la raíz (pruebas).
 */
public record AppPaths(Path root) {

    public static AppPaths fromEnvironment() {
        String override = System.getProperty("tunai.home");
        if (override != null && !override.isBlank()) {
            return new AppPaths(Path.of(override));
        }
        String appData = System.getenv("APPDATA");
        Path base = appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"), "AppData", "Roaming");
        return new AppPaths(base.resolve("TunAI"));
    }

    public Path installationIdFile() {
        return root.resolve("installation-id");
    }

    public Path settingsFile() {
        return root.resolve("settings.json");
    }

    public Path garageDir() {
        return root.resolve("garage");
    }

    public Path logsDir() {
        return root.resolve("logs");
    }
}
