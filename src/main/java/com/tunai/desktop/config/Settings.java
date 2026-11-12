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

    /** Cuenta a la que pertenece {@link #credits}: al entrar con otra, el saldo se vuelve a pedir al backend. */
    public String account;

    /** Sesión iniciada; nula si hay que pedir las credenciales. Guarda el token, nunca la contraseña. */
    public Session session;

    /** Sidebar expandido (240 px) o compacto (72 px). */
    public boolean sidebarExpanded;

    public Classifier classifier = new Classifier();

    public boolean hasConsent() {
        return consentAcceptedAt != null;
    }

    /** Hay un token que aún no caducó (con un minuto de margen). */
    public boolean hasValidSession(Instant now) {
        return session != null && session.token != null && !session.token.isBlank()
                && session.expiresAt != null && session.expiresAt.isAfter(now.plusSeconds(60));
    }

    public static class Session {
        public String username;
        public String token;
        public Instant expiresAt;
    }

    public static class Classifier {
        /** Salida de emergencia para la demo (§5.3.1); no se expone en la UI. */
        public boolean enabled = true;
        /** Umbral de la suma de probabilidades de clases de vehículo en el top 5. */
        public double threshold = 0.45;
    }
}
