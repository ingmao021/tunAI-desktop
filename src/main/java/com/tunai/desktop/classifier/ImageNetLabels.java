package com.tunai.desktop.classifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Etiquetas de ImageNet y lista de clases de vehículo. Los índices salen del archivo de etiquetas
 * que acompaña al modelo; nunca se escriben a mano (§5.3.1).
 */
public final class ImageNetLabels {

    static final String LABELS_RESOURCE = "/com/tunai/desktop/classifier/imagenet-labels.txt";
    static final String VEHICLES_RESOURCE = "/com/tunai/desktop/classifier/vehicle-classes.txt";

    private final List<String> names;
    private final boolean[] vehicle;

    private ImageNetLabels(List<String> names, Set<String> vehicleNames) {
        this.names = names;
        this.vehicle = new boolean[names.size()];
        Set<String> missing = new HashSet<>(vehicleNames);
        for (int i = 0; i < names.size(); i++) {
            if (vehicleNames.contains(names.get(i))) {
                vehicle[i] = true;
                missing.remove(names.get(i));
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Clases de vehículo que no existen en las etiquetas: " + missing);
        }
    }

    /**
     * @param vehicleOverride {@code vehicle-classes.txt} en la carpeta de datos; si existe reemplaza la lista incluida
     */
    public static ImageNetLabels load(Path vehicleOverride) throws IOException {
        List<String> names = new ArrayList<>(1000);
        try (BufferedReader r = reader(ImageNetLabels.class.getResourceAsStream(LABELS_RESOURCE))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isBlank()) {
                    names.add(primaryName(line));
                }
            }
        }
        List<String> vehicleLines;
        if (vehicleOverride != null && Files.exists(vehicleOverride)) {
            vehicleLines = Files.readAllLines(vehicleOverride, StandardCharsets.UTF_8);
        } else {
            try (BufferedReader r = reader(ImageNetLabels.class.getResourceAsStream(VEHICLES_RESOURCE))) {
                vehicleLines = r.lines().toList();
            }
        }
        Set<String> vehicleNames = new HashSet<>();
        for (String line : vehicleLines) {
            String s = line.strip();
            if (!s.isEmpty() && !s.startsWith("#")) {
                vehicleNames.add(s);
            }
        }
        return new ImageNetLabels(names, vehicleNames);
    }

    /** {@code "n04285008 sports car, sport car"} → {@code "sports car"}. */
    static String primaryName(String line) {
        String s = line.strip();
        int space = s.indexOf(' ');
        if (space > 0 && s.charAt(0) == 'n' && s.substring(1, space).chars().allMatch(Character::isDigit)) {
            s = s.substring(space + 1);
        }
        int comma = s.indexOf(',');
        return (comma >= 0 ? s.substring(0, comma) : s).strip();
    }

    private static BufferedReader reader(InputStream in) throws IOException {
        if (in == null) {
            throw new IOException("Recurso de etiquetas no encontrado");
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    public int size() {
        return names.size();
    }

    public String name(int index) {
        return names.get(index);
    }

    public boolean isVehicle(int index) {
        return vehicle[index];
    }

    public int vehicleClassCount() {
        int n = 0;
        for (boolean v : vehicle) {
            if (v) {
                n++;
            }
        }
        return n;
    }
}
