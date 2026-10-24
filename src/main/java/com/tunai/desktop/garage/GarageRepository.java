package com.tunai.desktop.garage;

import com.tunai.desktop.config.Json;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Garaje local (§4). Nunca se sube al servidor. */
public class GarageRepository {

    private static final Logger LOG = Logger.getLogger(GarageRepository.class.getName());

    private final Path garageDir;

    public GarageRepository(Path garageDir) {
        this.garageDir = garageDir;
    }

    /** Vehículos ordenados del más antiguo al más reciente. Las carpetas dañadas se ignoran. */
    public List<GarageVehicle> list() {
        List<GarageVehicle> vehicles = new ArrayList<>();
        if (!Files.isDirectory(garageDir)) {
            return vehicles;
        }
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(garageDir, Files::isDirectory)) {
            for (Path dir : dirs) {
                readVehicle(dir).ifPresent(vehicles::add);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo leer el garaje", e);
        }
        vehicles.sort(Comparator.comparing(v -> v.metadata().createdAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        return vehicles;
    }

    public Optional<GarageVehicle> find(String id) {
        return readVehicle(garageDir.resolve(id));
    }

    /**
     * Crea un vehículo con el GLB tal como llegó del backend. Se guarda de inmediato porque
     * cada generación consume cuota: si la app se cierra, el modelo no se pierde.
     */
    public GarageVehicle create(String name, byte[] originalGlb) throws IOException {
        VehicleMetadata meta = new VehicleMetadata();
        meta.id = UUID.randomUUID().toString();
        meta.name = name;
        meta.createdAt = Instant.now();
        meta.updatedAt = meta.createdAt;

        Path dir = garageDir.resolve(meta.id);
        Files.createDirectories(dir);
        writeAtomically(dir.resolve(GarageVehicle.ORIGINAL_GLB), originalGlb);
        writeMetadata(dir, meta);
        return new GarageVehicle(dir, meta);
    }

    /**
     * Guarda el color aplicado.
     *
     * @param editedTexturePng textura recoloreada; {@code null} borra la existente (color original)
     * @param thumbnailPng     captura del visor; {@code null} conserva la existente
     */
    public GarageVehicle saveEdits(GarageVehicle vehicle, VehicleMetadata.Paint paint,
                                   byte[] editedTexturePng, byte[] thumbnailPng) throws IOException {
        Path dir = vehicle.dir();
        Path edited = dir.resolve(GarageVehicle.EDITED_TEXTURE);
        if (editedTexturePng != null) {
            writeAtomically(edited, editedTexturePng);
        } else {
            Files.deleteIfExists(edited);
        }
        if (thumbnailPng != null) {
            writeAtomically(dir.resolve(GarageVehicle.THUMBNAIL), thumbnailPng);
        }
        VehicleMetadata meta = vehicle.metadata();
        meta.paint = editedTexturePng != null ? paint : null;
        meta.updatedAt = Instant.now();
        writeMetadata(dir, meta);
        return new GarageVehicle(dir, meta);
    }

    public void saveThumbnail(GarageVehicle vehicle, byte[] thumbnailPng) throws IOException {
        writeAtomically(vehicle.dir().resolve(GarageVehicle.THUMBNAIL), thumbnailPng);
    }

    public GarageVehicle rename(GarageVehicle vehicle, String newName) throws IOException {
        VehicleMetadata meta = vehicle.metadata();
        meta.name = newName;
        meta.updatedAt = Instant.now();
        writeMetadata(vehicle.dir(), meta);
        return new GarageVehicle(vehicle.dir(), meta);
    }

    public void delete(GarageVehicle vehicle) throws IOException {
        Path dir = vehicle.dir();
        if (!dir.startsWith(garageDir)) {
            throw new IOException("Ruta fuera del garaje: " + dir);
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    private Optional<GarageVehicle> readVehicle(Path dir) {
        Path metaFile = dir.resolve(GarageVehicle.METADATA);
        if (!Files.exists(metaFile) || !Files.exists(dir.resolve(GarageVehicle.ORIGINAL_GLB))) {
            return Optional.empty();
        }
        try {
            VehicleMetadata meta = Json.MAPPER.readValue(metaFile.toFile(), VehicleMetadata.class);
            meta.id = dir.getFileName().toString();
            return Optional.of(new GarageVehicle(dir, meta));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "vehicle.json dañado en " + dir, e);
            return Optional.empty();
        }
    }

    private static void writeMetadata(Path dir, VehicleMetadata meta) throws IOException {
        writeAtomically(dir.resolve(GarageVehicle.METADATA), Json.MAPPER.writeValueAsBytes(meta));
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, content);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
