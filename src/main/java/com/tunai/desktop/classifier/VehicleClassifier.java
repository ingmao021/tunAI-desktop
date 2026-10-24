package com.tunai.desktop.classifier;

import java.awt.image.BufferedImage;
import java.util.List;

/** ¿La foto es de un vehículo? (§5.3.1). Corre sin conexión y no consume cuota. */
public interface VehicleClassifier {

    /**
     * @throws ClassifierUnavailableException si el modelo no cargó; la generación queda bloqueada
     */
    Result classify(BufferedImage image) throws ClassifierUnavailableException;

    record Prediction(String label, double probability, boolean vehicle) {
    }

    /**
     * @param vehicleScore suma de probabilidades de las clases de vehículo dentro del top 5
     */
    record Result(boolean isVehicle, double vehicleScore, List<Prediction> top5) {
    }

    class ClassifierUnavailableException extends Exception {
        public ClassifierUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
