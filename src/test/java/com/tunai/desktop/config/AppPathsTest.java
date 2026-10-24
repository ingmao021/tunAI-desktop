package com.tunai.desktop.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Carpeta de datos de cada sistema y migración desde la ruta de la 1.0.0 en macOS y Linux. */
class AppPathsTest {

    private static final Path HOME = Path.of("/home/ana");

    @TempDir
    Path home;

    @Test
    void windowsUsesAppData() {
        Path appData = Path.of("C:", "Users", "ana", "AppData", "Roaming");
        assertEquals(appData.resolve("TunAI"),
                AppPaths.defaultRoot("Windows 11", Map.of("APPDATA", appData.toString()), HOME));
    }

    @Test
    void macUsesApplicationSupport() {
        assertEquals(HOME.resolve("Library/Application Support/TunAI"),
                AppPaths.defaultRoot("Mac OS X", Map.of(), HOME));
    }

    @Test
    void linuxUsesXdgDataHomeOrLocalShare() {
        assertEquals(HOME.resolve(".local/share/TunAI"), AppPaths.defaultRoot("Linux", Map.of(), HOME));
        Path xdg = HOME.resolve("datos").toAbsolutePath();
        assertEquals(xdg.resolve("TunAI"),
                AppPaths.defaultRoot("Linux", Map.of("XDG_DATA_HOME", xdg.toString()), HOME));
        // La especificación XDG manda ignorar una ruta relativa
        assertEquals(HOME.resolve(".local/share/TunAI"),
                AppPaths.defaultRoot("Linux", Map.of("XDG_DATA_HOME", "relativa"), HOME));
    }

    @Test
    void legacyFolderIsMovedOnMacAndLinux() throws Exception {
        Path legacy = home.resolve("AppData/Roaming/TunAI");
        Files.createDirectories(legacy.resolve("garage"));
        Files.writeString(legacy.resolve("settings.json"), "{}");
        Path root = AppPaths.defaultRoot("Linux", Map.of(), home);

        assertEquals(root, AppPaths.migrateLegacy("Linux", home, root));
        assertTrue(Files.exists(root.resolve("settings.json")));
        assertTrue(Files.isDirectory(root.resolve("garage")));
        assertFalse(Files.exists(legacy));
    }

    @Test
    void existingNewFolderIsNeverOverwritten() throws Exception {
        Path legacy = home.resolve("AppData/Roaming/TunAI");
        Files.createDirectories(legacy);
        Path root = AppPaths.defaultRoot("Mac OS X", Map.of(), home);
        Files.createDirectories(root);
        Files.writeString(root.resolve("settings.json"), "{\"nuevo\":true}");

        assertEquals(root, AppPaths.migrateLegacy("Mac OS X", home, root));
        assertEquals("{\"nuevo\":true}", Files.readString(root.resolve("settings.json")));
        assertTrue(Files.isDirectory(legacy));
    }

    @Test
    void windowsNeverMigrates() throws Exception {
        Path legacy = home.resolve("AppData/Roaming/TunAI");
        Files.createDirectories(legacy);
        assertEquals(legacy, AppPaths.migrateLegacy("Windows 11", home, legacy));
        assertTrue(Files.isDirectory(legacy));
    }
}
