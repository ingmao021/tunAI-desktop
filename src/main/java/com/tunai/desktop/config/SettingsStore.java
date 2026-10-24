package com.tunai.desktop.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Lee y escribe {@code settings.json}. Un archivo dañado se trata como configuración vacía. */
public class SettingsStore {

    private static final Logger LOG = Logger.getLogger(SettingsStore.class.getName());

    private final Path file;
    private Settings settings;

    public SettingsStore(Path file) {
        this.file = file;
        this.settings = load();
    }

    public Settings get() {
        return settings;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Json.MAPPER.writeValue(tmp.toFile(), settings);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo guardar " + file, e);
        }
    }

    private Settings load() {
        if (!Files.exists(file)) {
            return new Settings();
        }
        try {
            Settings loaded = Json.MAPPER.readValue(file.toFile(), Settings.class);
            if (loaded.classifier == null) {
                loaded.classifier = new Settings.Classifier();
            }
            return loaded;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "settings.json dañado; se usan valores por defecto", e);
            return new Settings();
        }
    }
}
