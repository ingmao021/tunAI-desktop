package com.tunai.desktop.garage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Un vehículo guardado en {@code garage\<vehicleId>\}. */
public record GarageVehicle(Path dir, VehicleMetadata metadata) {

    static final String ORIGINAL_GLB = "original.glb";
    static final String EDITED_TEXTURE = "texture-edited.png";
    static final String THUMBNAIL = "thumbnail.png";
    static final String METADATA = "vehicle.json";

    public String id() {
        return metadata.id;
    }

    public String name() {
        return metadata.name;
    }

    public Path originalGlb() {
        return dir.resolve(ORIGINAL_GLB);
    }

    public Optional<Path> editedTexture() {
        return existing(EDITED_TEXTURE);
    }

    public Optional<Path> thumbnail() {
        return existing(THUMBNAIL);
    }

    private Optional<Path> existing(String fileName) {
        Path p = dir.resolve(fileName);
        return Files.exists(p) ? Optional.of(p) : Optional.empty();
    }
}
