package com.tunai.desktop.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Marcador de carga con la forma del contenido real: bloque gris claro que pulsa suavemente. La animación solo corre
 * mientras el nodo está en pantalla.
 */
final class Skeleton extends Region {

    private final FadeTransition pulse = new FadeTransition(Duration.millis(900), this);

    Skeleton(double width, double height) {
        getStyleClass().add("skeleton");
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.45);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        sceneProperty().addListener((o, old, scene) -> {
            if (scene != null) {
                pulse.play();
            } else {
                pulse.stop();
            }
        });
        visibleProperty().addListener((o, was, now) -> {
            if (now && getScene() != null) {
                pulse.play();
            } else {
                pulse.pause();
            }
        });
    }

    /** Línea de texto. */
    static Skeleton line(double width) {
        Skeleton s = new Skeleton(width, 12);
        s.getStyleClass().add("skeleton-line");
        return s;
    }

    /** Bloque que ocupa todo el ancho disponible (p. ej. la zona de una miniatura). */
    static Skeleton block(double width, double height) {
        return new Skeleton(width, height);
    }
}
