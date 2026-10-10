package com.tunai.desktop.ui;

import com.tunai.desktop.AppContext;
import com.tunai.desktop.garage.GarageVehicle;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Exportar un vehículo del garaje como GLB. */
final class VehicleExport {

    private static final Logger LOG = Logger.getLogger(VehicleExport.class.getName());

    private VehicleExport() {
    }

    static void exportGlb(AppContext context, GarageVehicle vehicle) {
        File target = chooseTarget(context, vehicle.name(), "Modelo GLB", "*.glb");
        if (target == null) {
            return;
        }
        try {
            Files.copy(vehicle.originalGlb(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo exportar " + vehicle.id(), e);
            Dialogs.info(context.stage(), "Exportar", "No se pudo exportar el modelo.");
        }
    }

    static File chooseTarget(AppContext context, String baseName, String description, String pattern) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, pattern));
        chooser.setInitialFileName(safeFileName(baseName) + pattern.substring(1));
        String last = context.settings().get().lastFolder;
        if (last != null && Files.isDirectory(Path.of(last))) {
            chooser.setInitialDirectory(new File(last));
        }
        File target = chooser.showSaveDialog(context.stage());
        if (target != null && target.getParentFile() != null) {
            context.settings().get().lastFolder = target.getParent();
            context.settings().save();
        }
        return target;
    }

    private static String safeFileName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return cleaned.isEmpty() ? "vehiculo" : cleaned;
    }
}
