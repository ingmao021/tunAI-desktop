package com.tunai.desktop.classifier;

import com.tunai.desktop.image.Photo;
import com.tunai.desktop.image.PhotoDecoder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Calibración del clasificador (§5.3.1). Uso:
 * <pre>
 *   CalibrationTool &lt;carpeta&gt; [salida.md] [umbral]
 * </pre>
 * {@code <carpeta>} contiene {@code vehiculos/} (fotos de carros) y {@code otros/} (no carros).
 * Escribe la tabla foto / top 5 / suma / decisión y propone el umbral más bajo con 0 falsos positivos.
 */
public final class CalibrationTool {

    private record Row(String group, String file, VehicleClassifier.Result result, long millis) {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        Path output = args.length > 1 ? Path.of(args[1]) : Path.of("docs", "calibracion-clasificador.md");
        double threshold = args.length > 2 ? Double.parseDouble(args[2]) : 0.40;

        OnnxVehicleClassifier classifier = new OnnxVehicleClassifier(() -> threshold, null);
        classifier.load();
        List<Row> rows = new ArrayList<>();
        for (String group : List.of("vehiculos", "otros")) {
            for (Path file : images(dir.resolve(group))) {
                byte[] bytes = Files.readAllBytes(file);
                Photo.Format format = bytes.length > 1 && (bytes[0] & 0xFF) == 0x89 ? Photo.Format.PNG : Photo.Format.JPEG;
                BufferedImage image = PhotoDecoder.decode(new Photo(file.getFileName().toString(), bytes, format, 0, 0));
                long start = System.nanoTime();
                VehicleClassifier.Result result = classifier.classify(image);
                rows.add(new Row(group, file.getFileName().toString(), result, (System.nanoTime() - start) / 1_000_000));
            }
        }
        classifier.close();

        String report = report(rows, threshold);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
        System.out.println(report);
    }

    private static List<Path> images(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(jpe?g|png)"))
                    .sorted().toList();
        }
    }

    private static String report(List<Row> rows, double threshold) {
        double maxOther = rows.stream().filter(r -> r.group.equals("otros"))
                .mapToDouble(r -> r.result.vehicleScore()).max().orElse(0);
        long falsePositives = rows.stream().filter(r -> r.group.equals("otros") && r.result.isVehicle()).count();
        long falseNegatives = rows.stream().filter(r -> r.group.equals("vehiculos") && !r.result.isVehicle()).count();
        long cars = rows.stream().filter(r -> r.group.equals("vehiculos")).count();
        long others = rows.size() - cars;
        double avgMs = rows.stream().skip(1).mapToLong(r -> r.millis).average().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("# Calibración del clasificador de vehículos\n\n");
        sb.append("Generado por `CalibrationTool` el ").append(LocalDate.now()).append(". Modelo: MobileNetV2 (ONNX Model Zoo, `mobilenetv2-12.onnx`).\n\n");
        sb.append(String.format(Locale.ROOT, "- Umbral probado: **%.2f**%n", threshold));
        sb.append(String.format(Locale.ROOT, "- Fotos: %d de vehículos, %d de otras cosas%n", cars, others));
        sb.append(String.format(Locale.ROOT, "- Falsos positivos (no carro aceptado): **%d**%n", falsePositives));
        sb.append(String.format(Locale.ROOT, "- Falsos negativos (carro rechazado): **%d** de %d%n", falseNegatives, cars));
        sb.append(String.format(Locale.ROOT, "- Suma más alta entre las fotos que no son carros: %.3f%n", maxOther));
        sb.append(String.format(Locale.ROOT, "- Tiempo medio por foto (sin contar la primera): %.0f ms%n%n", avgMs));
        sb.append("| Grupo | Foto | Top 5 | Suma vehículo | Decisión | Resultado |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (Row r : rows) {
            String top = r.result.top5().stream()
                    .map(p -> String.format(Locale.ROOT, "%s%s %.1f%%", p.vehicle() ? "🚗 " : "", p.label(), p.probability() * 100))
                    .collect(Collectors.joining("<br>"));
            boolean correct = r.group.equals("vehiculos") == r.result.isVehicle();
            sb.append("| ").append(r.group).append(" | ").append(r.file).append(" | ").append(top).append(" | ")
                    .append(String.format(Locale.ROOT, "%.3f", r.result.vehicleScore())).append(" | ")
                    .append(r.result.isVehicle() ? "vehículo" : "no vehículo").append(" | ")
                    .append(correct ? "✓" : (r.group.equals("otros") ? "✗ falso positivo" : "✗ falso negativo"))
                    .append(" |\n");
        }
        return sb.toString();
    }
}
