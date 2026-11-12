package com.tunai.desktop.viewer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Encuadre inicial: los vehículos bajos conservan la distancia de siempre y los modelos altos se alejan. */
class ModelViewerFitTest {

    /** Dos puntos opuestos: solo importa la extensión vertical (Y, índice 1 de cada vértice). */
    private static float[] box(float height) {
        return new float[]{-100, -height / 2, 0, 100, height / 2, 0};
    }

    @Test
    void lowVehicleKeepsDefaultDistance() {
        assertEquals(ModelViewer.INITIAL_DISTANCE, ModelViewer.fitDistance(box(70)), 1e-9);
    }

    @Test
    void tallModelMovesBackToFitVertically() {
        double d = ModelViewer.fitDistance(box(GlbModel.TARGET_SIZE));
        assertTrue(d > ModelViewer.INITIAL_DISTANCE, "Un modelo de 200 de alto no cabe a la distancia por defecto: " + d);
        double visibleHalf = d * Math.tan(Math.toRadians(ModelViewer.FIELD_OF_VIEW / 2));
        assertTrue(visibleHalf >= GlbModel.TARGET_SIZE / 2.0 * ModelViewer.FIT_MARGIN - 1e-6);
    }

    @Test
    void distanceGrowsWithHeightAndIsBounded() {
        assertTrue(ModelViewer.fitDistance(box(200)) <= ModelViewer.fitDistance(box(260)));
        assertEquals(ModelViewer.MAX_DISTANCE, ModelViewer.fitDistance(box(100_000)), 1e-9);
    }

    @Test
    void emptyModelFallsBackToDefault() {
        assertEquals(ModelViewer.INITIAL_DISTANCE, ModelViewer.fitDistance(new float[0]), 1e-9);
    }
}
