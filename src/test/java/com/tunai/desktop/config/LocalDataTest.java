package com.tunai.desktop.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDataTest {

    @TempDir
    Path home;

    @Test
    void installationIdIsStableAcrossLoads() {
        AppPaths paths = new AppPaths(home);
        UUID first = InstallationId.loadOrCreate(paths.installationIdFile()).value();
        UUID second = InstallationId.loadOrCreate(paths.installationIdFile()).value();
        assertEquals(first, second);
        assertEquals(4, first.version());
    }

    @Test
    void corruptInstallationIdIsRegenerated() throws Exception {
        AppPaths paths = new AppPaths(home);
        Files.createDirectories(home);
        Files.writeString(paths.installationIdFile(), "no-es-un-uuid");
        UUID id = InstallationId.loadOrCreate(paths.installationIdFile()).value();
        assertEquals(id.toString(), Files.readString(paths.installationIdFile()));
    }

    @Test
    void regenerateChangesId() {
        InstallationId id = InstallationId.loadOrCreate(new AppPaths(home).installationIdFile());
        UUID before = id.value();
        assertNotEquals(before, id.regenerate());
    }

    @Test
    void consentPersists() {
        Path file = new AppPaths(home).settingsFile();
        SettingsStore store = new SettingsStore(file);
        assertFalse(store.get().hasConsent());
        store.get().consentAcceptedAt = Instant.parse("2026-10-09T12:00:00Z");
        store.save();

        SettingsStore reloaded = new SettingsStore(file);
        assertTrue(reloaded.get().hasConsent());
        assertEquals(Instant.parse("2026-10-09T12:00:00Z"), reloaded.get().consentAcceptedAt);
        assertTrue(reloaded.get().classifier.enabled);
    }

    @Test
    void classifierCanBeDisabledFromSettingsFile() throws Exception {
        Path file = new AppPaths(home).settingsFile();
        Files.createDirectories(home);
        Files.writeString(file, "{\"classifier\": {\"enabled\": false}, \"campoNuevo\": 1}");
        Settings settings = new SettingsStore(file).get();
        assertFalse(settings.classifier.enabled);
        assertEquals(0.45, settings.classifier.threshold);
    }

    @Test
    void corruptSettingsFallBackToDefaults() throws Exception {
        Path file = new AppPaths(home).settingsFile();
        Files.createDirectories(home);
        Files.writeString(file, "{roto");
        assertFalse(new SettingsStore(file).get().hasConsent());
    }
}
