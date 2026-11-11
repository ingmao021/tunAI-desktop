package com.tunai.desktop.ui;

import com.tunai.desktop.garage.GarageRepository;
import com.tunai.desktop.garage.GarageVehicle;
import com.tunai.desktop.viewer.UnsupportedModelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Abrir modelo (.glb) desde el disco: validación §3 y guardado en el garaje. */
class VehicleImportTest {

    @TempDir
    Path tmp;

    private GarageRepository garage() {
        return new GarageRepository(tmp.resolve("garage"));
    }

    private Path copyResource(String resource, String fileName) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            Path file = tmp.resolve(fileName);
            Files.write(file, in.readAllBytes());
            return file;
        }
    }

    @Test
    void importaUnGlbCompatibleConElNombreDelArchivo() throws Exception {
        Path file = copyResource("/glb/BoxTextured.glb", "Mazda roja.glb");
        GarageRepository garage = garage();

        GarageVehicle vehicle = VehicleImport.importFile(garage, file);

        assertEquals("Mazda roja", vehicle.name());
        assertTrue(Files.exists(vehicle.originalGlb()));
        assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(vehicle.originalGlb()));
        assertEquals(1, garage.list().size());
    }

    @Test
    void rechazaUnGlbIncompatibleSinTocarElGaraje() throws Exception {
        Path file = copyResource("/glb/Box.glb", "Box.glb");
        GarageRepository garage = garage();

        assertThrows(UnsupportedModelException.class, () -> VehicleImport.importFile(garage, file));
        assertTrue(garage.list().isEmpty());
    }

    @Test
    void rechazaUnArchivoDeMasDe50Mb() throws Exception {
        Path file = tmp.resolve("gigante.glb");
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(VehicleImport.MAX_BYTES + 1);
        }

        GarageRepository garage = garage();
        UnsupportedModelException e =
                assertThrows(VehicleImport.ModelTooLargeException.class, () -> VehicleImport.importFile(garage, file));
        assertEquals(VehicleImport.ERR_TOO_LARGE, e.getMessage());
        assertTrue(garage.list().isEmpty());
    }

    @Test
    void nombreDelVehiculoSinExtension() {
        assertEquals("Twingo", VehicleImport.vehicleName(Path.of("C:/modelos/Twingo.glb")));
        assertEquals("Spark", VehicleImport.vehicleName(Path.of("Spark.GLB")));
        assertEquals("sin-extension", VehicleImport.vehicleName(Path.of("sin-extension")));
        assertEquals("Vehículo", VehicleImport.vehicleName(Path.of(".glb")));
    }
}
