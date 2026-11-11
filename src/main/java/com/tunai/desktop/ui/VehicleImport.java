package com.tunai.desktop.ui;

import com.tunai.desktop.AppContext;
import com.tunai.desktop.garage.GarageRepository;
import com.tunai.desktop.garage.GarageVehicle;
import com.tunai.desktop.viewer.GlbModel;
import com.tunai.desktop.viewer.UnsupportedModelException;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Abrir un modelo GLB del disco e importarlo al garaje: por ejemplo, un modelo exportado desde
 * otra instalación o el GLB de respaldo de un ensayo. No consume créditos ni requiere conexión,
 * y el archivo debe pasar las mismas validaciones del visor (§3) que un modelo del backend.
 */
final class VehicleImport {

    /** Mismo límite que acepta el backend para un GLB (RF-BE-013). */
    static final long MAX_BYTES = 50_000_000L;

    static final String ERR_TOO_LARGE = "El modelo pesa más de 50 MB.";

    /** El archivo supera {@link #MAX_BYTES}; permite distinguirlo de un GLB incompatible sin comparar textos. */
    static final class ModelTooLargeException extends UnsupportedModelException {
        ModelTooLargeException() {
            super(ERR_TOO_LARGE);
        }
    }

    private VehicleImport() {
    }

    /** Diálogo para elegir el GLB. Solo en el hilo de JavaFX. Devuelve {@code null} si se cancela. */
    static File chooseSource(AppContext context) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir modelo");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Modelo GLB", "*.glb", "*.GLB"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        String last = context.settings().get().lastFolder;
        if (last != null && isDirectory(last)) {
            chooser.setInitialDirectory(new File(last));
        }
        File file = chooser.showOpenDialog(context.stage());
        if (file != null && file.getParent() != null) {
            context.settings().get().lastFolder = file.getParent();
            context.settings().save();
        }
        return file;
    }

    /**
     * Lee el archivo, lo valida como en {@code GeneratingController#prepare} y lo guarda en el
     * garaje. Pensado para un hilo de fondo.
     *
     * @throws UnsupportedModelException si el GLB no tiene la forma que acepta el visor; si excede
     *                                   {@link #MAX_BYTES} es un {@link ModelTooLargeException}
     * @throws IOException               si el archivo no se puede leer o no se puede guardar
     */
    static GarageVehicle importFile(GarageRepository garage, Path file)
            throws IOException, UnsupportedModelException {
        byte[] glb;
        try (InputStream in = Files.newInputStream(file)) {
            glb = in.readNBytes((int) MAX_BYTES + 1);
        }
        if (glb.length > MAX_BYTES) {
            throw new ModelTooLargeException();
        }
        GlbModel.validate(glb);
        return garage.create(vehicleName(file), glb);
    }

    private static boolean isDirectory(String path) {
        try {
            return Files.isDirectory(Path.of(path));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /** Nombre para la tarjeta del garaje: el del archivo sin la extensión {@code .glb}. */
    static String vehicleName(Path file) {
        String name = file.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".glb")) {
            name = name.substring(0, name.length() - 4);
        }
        name = name.strip();
        return name.isEmpty() ? "Vehículo" : name;
    }
}
