package com.tunai.desktop.garage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarageRepositoryTest {

    private static final byte[] GLB = {'g', 'l', 'T', 'F', 2, 0, 0, 0};

    @TempDir
    Path garageDir;

    @Test
    void emptyGarageWhenFolderMissing() {
        assertTrue(new GarageRepository(garageDir.resolve("no-existe")).list().isEmpty());
    }

    @Test
    void createAndList() throws Exception {
        GarageRepository repo = new GarageRepository(garageDir);
        GarageVehicle created = repo.create("Mazda", GLB);

        List<GarageVehicle> vehicles = repo.list();
        assertEquals(1, vehicles.size());
        GarageVehicle v = vehicles.get(0);
        assertEquals(created.id(), v.id());
        assertEquals("Mazda", v.name());
        assertArrayEquals(GLB, Files.readAllBytes(v.originalGlb()));
        assertTrue(v.editedTexture().isEmpty());
        assertTrue(v.thumbnail().isEmpty());
    }

    @Test
    void saveEditsPersistsPaintAndRestoreRemovesIt() throws Exception {
        GarageRepository repo = new GarageRepository(garageDir);
        GarageVehicle v = repo.create("Twingo", GLB);

        repo.saveEdits(v, new VehicleMetadata.Paint("#C62828", 0.8), new byte[]{1, 2}, new byte[]{3});
        GarageVehicle reloaded = repo.find(v.id()).orElseThrow();
        assertEquals("#C62828", reloaded.metadata().paint.color);
        assertEquals(0.8, reloaded.metadata().paint.intensity);
        assertTrue(reloaded.editedTexture().isPresent());
        assertTrue(reloaded.thumbnail().isPresent());

        repo.saveEdits(reloaded, null, null, null);
        GarageVehicle restored = repo.find(v.id()).orElseThrow();
        assertNull(restored.metadata().paint);
        assertTrue(restored.editedTexture().isEmpty());
        assertTrue(restored.thumbnail().isPresent());
    }

    @Test
    void renameAndDelete() throws Exception {
        GarageRepository repo = new GarageRepository(garageDir);
        GarageVehicle v = repo.create("Spark", GLB);
        repo.rename(v, "Spark rojo");
        assertEquals("Spark rojo", repo.find(v.id()).orElseThrow().name());

        repo.delete(v);
        assertFalse(Files.exists(v.dir()));
        assertTrue(repo.list().isEmpty());
    }

    @Test
    void corruptVehicleIsSkipped() throws Exception {
        GarageRepository repo = new GarageRepository(garageDir);
        repo.create("Bueno", GLB);
        Path broken = Files.createDirectories(garageDir.resolve("roto"));
        Files.write(broken.resolve("original.glb"), GLB);
        Files.writeString(broken.resolve("vehicle.json"), "{no json");
        assertEquals(1, repo.list().size());
    }
}
