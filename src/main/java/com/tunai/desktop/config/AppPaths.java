package com.tunai.desktop.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Rutas de los datos locales (§4), en la carpeta de datos de usuario de cada sistema:
 * {@code %APPDATA%\TunAI} en Windows, {@code ~/Library/Application Support/TunAI} en macOS y
 * {@code $XDG_DATA_HOME/TunAI} (o {@code ~/.local/share/TunAI}) en Linux.
 * La propiedad de sistema {@code tunai.home} permite cambiar la raíz (pruebas).
 */
public record AppPaths(Path root) {

    private static final Logger LOG = Logger.getLogger(AppPaths.class.getName());
    private static final String DIR_NAME = "TunAI";

    public static AppPaths fromEnvironment() {
        String override = System.getProperty("tunai.home");
        if (override != null && !override.isBlank()) {
            return new AppPaths(Path.of(override));
        }
        String os = System.getProperty("os.name", "");
        Path home = Path.of(System.getProperty("user.home"));
        Path root = defaultRoot(os, System.getenv(), home);
        return new AppPaths(migrateLegacy(os, home, root));
    }

    /** Carpeta de datos de usuario del sistema; no toca el disco. */
    static Path defaultRoot(String osName, Map<String, String> env, Path userHome) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            String appData = env.get("APPDATA");
            return (isSet(appData) ? Path.of(appData) : legacyBase(userHome)).resolve(DIR_NAME);
        }
        if (os.startsWith("mac")) {
            return userHome.resolve("Library").resolve("Application Support").resolve(DIR_NAME);
        }
        String xdg = env.get("XDG_DATA_HOME");
        Path base = isSet(xdg) && Path.of(xdg).isAbsolute() ? Path.of(xdg) : userHome.resolve(".local").resolve("share");
        return base.resolve(DIR_NAME);
    }

    /**
     * La 1.0.0 guardaba los datos en {@code ~/AppData/Roaming/TunAI} también en macOS y Linux: si esa carpeta existe y
     * la nueva no, se mueve para no perder el garaje ni los créditos. Si no se puede mover, se sigue usando la antigua.
     */
    static Path migrateLegacy(String osName, Path userHome, Path root) {
        if (osName.toLowerCase(Locale.ROOT).startsWith("windows")) {
            return root;
        }
        Path legacy = legacyBase(userHome).resolve(DIR_NAME);
        if (!Files.isDirectory(legacy) || Files.exists(root)) {
            return root;
        }
        try {
            Files.createDirectories(root.getParent());
            Files.move(legacy, root);
            LOG.info("Datos movidos de " + legacy + " a " + root);
            return root;
        } catch (IOException e) {
            LOG.warning("No se pudieron mover los datos de " + legacy + ": " + e + "; se sigue usando esa carpeta");
            return legacy;
        }
    }

    private static Path legacyBase(Path userHome) {
        return userHome.resolve("AppData").resolve("Roaming");
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
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
