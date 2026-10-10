package com.tunai.desktop.viewer;

/** El GLB no tiene la forma de SF3D (§3): no se intenta renderizarlo a medias. */
public class UnsupportedModelException extends Exception {

    public UnsupportedModelException(String message) {
        super(message);
    }

    public UnsupportedModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
