package com.tunai.desktop.config;

import java.time.Instant;

/** Contenido de {@code settings.json}. Los campos nulos usan el valor por defecto. */
public class Settings {

    /** Fecha en que el usuario aceptó el envío de fotos (§5.1). Nulo si no ha aceptado. */
    public Instant consentAcceptedAt;

    /** Última carpeta usada al elegir una foto o exportar. */
    public String lastFolder;

    /** Base URL del backend. La variable de entorno {@code TUNAI_API_URL} tiene prioridad. */
    public String apiBaseUrl;

    /** Créditos que le quedan a esta instalación. Nulo = recién instalada (se reciben los iniciales). */
    public Integer credits;

    public Classifier classifier = new Classifier();

    public boolean hasConsent() {
        return consentAcceptedAt != null;
    }

    public static class Classifier {
        /** Salida de emergencia para la demo (§5.3.1); no se expone en la UI. */
        public boolean enabled = true;
        /** Umbral de la suma de probabilidades de clases de vehículo en el top 5. */
        public double threshold = 0.45;
    }
}
