package com.tunai.desktop.garage;

import java.time.Instant;

/** Contenido de {@code vehicle.json} (§4). */
public class VehicleMetadata {

    public static final int CURRENT_VERSION = 1;

    public String id;
    public String name;
    public Instant createdAt;
    public Instant updatedAt;
    /** Color aplicado; nulo si el vehículo conserva su color original. */
    public Paint paint;
    public int version = CURRENT_VERSION;

    public static class Paint {
        /** Color en formato {@code #RRGGBB}. */
        public String color;
        /** Mezcla entre el color nuevo y el original, de 0 a 1. */
        public double intensity;

        public Paint() {
        }

        public Paint(String color, double intensity) {
            this.color = color;
            this.intensity = intensity;
        }
    }
}
