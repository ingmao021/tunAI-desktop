package com.tunai.desktop.api;

import com.tunai.desktop.config.Settings;

import java.net.URI;

/** Base URL del backend (§6). */
public final class ApiConfig {

    public static final String PRODUCTION_URL = "https://tunai-api-qd5x.onrender.com";
    public static final String DEVELOPMENT_URL = "http://localhost:8080";

    private ApiConfig() {
    }

    /**
     * Prioridad: propiedad de sistema {@code tunai.api.url} (pruebas), variable de entorno
     * {@code TUNAI_API_URL}, {@code apiBaseUrl} en {@code settings.json} y por último producción.
     */
    public static URI resolveBaseUrl(Settings settings) {
        String url = firstNonBlank(
                System.getProperty("tunai.api.url"),
                System.getenv("TUNAI_API_URL"),
                settings.apiBaseUrl,
                PRODUCTION_URL);
        return URI.create(url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        throw new IllegalStateException();
    }
}
