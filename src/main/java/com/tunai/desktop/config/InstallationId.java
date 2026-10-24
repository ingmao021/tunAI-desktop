package com.tunai.desktop.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * UUID v4 que identifica esta instalación (§4). Se crea en el primer inicio y no cambia;
 * si el archivo falta o está dañado se genera uno nuevo.
 */
public final class InstallationId {

    private static final Logger LOG = Logger.getLogger(InstallationId.class.getName());

    private final Path file;
    private UUID value;

    private InstallationId(Path file, UUID value) {
        this.file = file;
        this.value = value;
    }

    public static InstallationId loadOrCreate(Path file) {
        UUID existing = read(file);
        if (existing != null) {
            return new InstallationId(file, existing);
        }
        InstallationId id = new InstallationId(file, null);
        id.regenerate();
        return id;
    }

    public synchronized UUID value() {
        return value;
    }

    /** Crea un ID nuevo. Se usa también ante {@code INVALID_INSTALLATION_ID} (§7). */
    public synchronized UUID regenerate() {
        UUID fresh = UUID.randomUUID();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, fresh.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir " + file, e);
        }
        LOG.info("Nuevo installation-id generado");
        value = fresh;
        return fresh;
    }

    private static UUID read(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(Files.readString(file, StandardCharsets.UTF_8).trim());
            return uuid.version() == 4 ? uuid : null;
        } catch (IOException | IllegalArgumentException e) {
            LOG.warning("installation-id dañado; se generará uno nuevo");
            return null;
        }
    }
}
