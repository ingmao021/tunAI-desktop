package com.tunai.desktop.classifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleClassifierTest {

    @TempDir
    Path dir;

    @Test
    void labelsComeFromFileAndAllVehicleClassesExist() throws Exception {
        ImageNetLabels labels = ImageNetLabels.load(null);
        assertEquals(1000, labels.size());
        assertEquals(16, labels.vehicleClassCount());
        assertEquals("sports car", labels.name(817));
        assertTrue(labels.isVehicle(817));
        assertFalse(labels.isVehicle(479), "car wheel queda fuera");
        assertFalse(labels.isVehicle(581), "grille queda fuera");
    }

    @Test
    void primaryNameStripsSynsetAndAliases() {
        assertEquals("sports car", ImageNetLabels.primaryName("n04285008 sports car, sport car"));
        assertEquals("Model T", ImageNetLabels.primaryName("n03777568 Model T"));
    }

    @Test
    void overrideFileReplacesVehicleList() throws Exception {
        Path override = dir.resolve("vehicle-classes.txt");
        Files.writeString(override, "# solo deportivos\nsports car\n");
        assertEquals(1, ImageNetLabels.load(override).vehicleClassCount());

        Files.writeString(override, "no existe\n");
        assertThrows(IllegalArgumentException.class, () -> ImageNetLabels.load(override));
    }

    @Test
    void softmaxSumsToOne() {
        double[] p = OnnxVehicleClassifier.softmax(new float[]{1f, 2f, 3f, 1000f});
        double sum = 0;
        for (double v : p) {
            sum += v;
        }
        assertEquals(1.0, sum, 1e-9);
        assertTrue(p[3] > 0.99);
    }

    @Test
    void decisionSumsVehicleClassesInTop5() throws Exception {
        ImageNetLabels labels = ImageNetLabels.load(null);
        double[] p = new double[1000];
        p[817] = 0.25; // sports car
        p[511] = 0.20; // convertible
        p[479] = 0.30; // car wheel (no cuenta)
        p[1] = 0.10;
        p[2] = 0.10;
        p[3] = 0.05; // fuera del top 5

        VehicleClassifier.Result result = OnnxVehicleClassifier.decide(p, labels, 0.45);
        assertEquals(5, result.top5().size());
        assertEquals(0.45, result.vehicleScore(), 1e-9);
        assertTrue(result.isVehicle());
        assertFalse(OnnxVehicleClassifier.decide(p, labels, 0.46).isVehicle());
    }

    @Test
    void preprocessingProducesNormalizedNchwTensor() {
        BufferedImage img = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 1000, 600);
        g.dispose();
        float[] t = ImagePreprocessor.toTensor(img);
        assertEquals(3 * 224 * 224, t.length);
        int plane = 224 * 224;
        assertEquals((1 - 0.485f) / 0.229f, t[0], 1e-4);
        assertEquals((1 - 0.456f) / 0.224f, t[plane], 1e-4);
        assertEquals((1 - 0.406f) / 0.225f, t[2 * plane + plane - 1], 1e-4);
    }

    /** CA-UI-14: carga el modelo real y clasifica en menos de 1 s, sin conexión. */
    @Test
    void realModelClassifiesQuickly() throws Exception {
        OnnxVehicleClassifier classifier = new OnnxVehicleClassifier(() -> 0.45, null);
        classifier.load();
        BufferedImage img = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        classifier.classify(img); // calentamiento
        long start = System.nanoTime();
        VehicleClassifier.Result r = classifier.classify(img);
        long ms = (System.nanoTime() - start) / 1_000_000;
        classifier.close();
        assertEquals(5, r.top5().size());
        assertFalse(r.isVehicle(), "Una imagen negra no es un vehículo");
        assertTrue(ms < 1000, "Tardó " + ms + " ms");
    }
}
