package com.tunai.desktop.classifier;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** MobileNetV2 (ImageNet) con ONNX Runtime (§5.3.1). */
public class OnnxVehicleClassifier implements VehicleClassifier, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(OnnxVehicleClassifier.class.getName());
    static final String MODEL_RESOURCE = "/com/tunai/desktop/classifier/mobilenetv2-12.onnx";
    static final int TOP_K = 5;

    private final DoubleSupplier threshold;
    private final Path vehicleClassesOverride;
    private OrtSession session;
    private String inputName;
    private ImageNetLabels labels;
    private Exception loadError;

    /**
     * @param threshold umbral leído en cada clasificación (configurable en {@code settings.json})
     */
    public OnnxVehicleClassifier(DoubleSupplier threshold, Path vehicleClassesOverride) {
        this.threshold = threshold;
        this.vehicleClassesOverride = vehicleClassesOverride;
    }

    /** Carga el modelo. Se llama al iniciar la app, en segundo plano, para que la primera foto sea rápida. */
    public synchronized void load() {
        if (session != null || loadError != null) {
            return;
        }
        try (InputStream in = OnnxVehicleClassifier.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (in == null) {
                throw new IOException("Modelo no encontrado: " + MODEL_RESOURCE);
            }
            labels = ImageNetLabels.load(vehicleClassesOverride);
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            session = env.createSession(in.readAllBytes(), options);
            inputName = session.getInputNames().iterator().next();
            LOG.info("Clasificador cargado: " + labels.vehicleClassCount() + " clases de vehículo, umbral "
                    + threshold.getAsDouble());
        } catch (IOException | OrtException | RuntimeException | UnsatisfiedLinkError e) {
            loadError = e instanceof Exception ex ? ex : new IllegalStateException(e);
            LOG.log(Level.SEVERE, "No se pudo cargar el clasificador de vehículos", e);
        }
    }

    @Override
    public Result classify(BufferedImage image) throws ClassifierUnavailableException {
        load();
        if (session == null) {
            throw new ClassifierUnavailableException("Clasificador no disponible", loadError);
        }
        float[] logits;
        try {
            float[] input = ImagePreprocessor.toTensor(image);
            long[] shape = {1, 3, ImagePreprocessor.SIZE, ImagePreprocessor.SIZE};
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape);
                 OrtSession.Result out = session.run(Map.of(inputName, tensor))) {
                logits = ((float[][]) out.get(0).getValue())[0];
            }
        } catch (OrtException | RuntimeException e) {
            LOG.log(Level.SEVERE, "Error al clasificar", e);
            throw new ClassifierUnavailableException("Error al clasificar", e);
        }
        return decide(softmax(logits), labels, threshold.getAsDouble());
    }

    static Result decide(double[] probabilities, ImageNetLabels labels, double threshold) {
        List<Integer> order = new ArrayList<>(probabilities.length);
        for (int i = 0; i < probabilities.length; i++) {
            order.add(i);
        }
        order.sort((a, b) -> Double.compare(probabilities[b], probabilities[a]));
        List<Prediction> top = new ArrayList<>(TOP_K);
        double vehicleScore = 0;
        for (int i = 0; i < TOP_K && i < order.size(); i++) {
            int idx = order.get(i);
            boolean vehicle = labels.isVehicle(idx);
            top.add(new Prediction(labels.name(idx), probabilities[idx], vehicle));
            if (vehicle) {
                vehicleScore += probabilities[idx];
            }
        }
        return new Result(vehicleScore >= threshold, vehicleScore, Collections.unmodifiableList(top));
    }

    static double[] softmax(float[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (float v : logits) {
            max = Math.max(max, v);
        }
        double sum = 0;
        double[] out = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            out[i] = Math.exp(logits[i] - max);
            sum += out[i];
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= sum;
        }
        return out;
    }

    @Override
    public synchronized void close() {
        if (session != null) {
            try {
                session.close();
            } catch (OrtException e) {
                LOG.log(Level.FINE, "Error al cerrar la sesión ONNX", e);
            }
            session = null;
        }
    }
}
