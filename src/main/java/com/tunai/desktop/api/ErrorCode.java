package com.tunai.desktop.api;

import java.util.Arrays;

/**
 * Catálogo de errores para el usuario (§7). La UI decide por {@code code}, nunca por el texto
 * de {@code detail}. Un código desconocido se trata como {@link #INTERNAL_ERROR}.
 */
public enum ErrorCode {

    // Backend
    INVALID_INSTALLATION_ID("Hubo un problema con la instalación. Reinicia la app.", Action.REGENERATE_ID),
    INVALID_IMAGE("No pudimos leer la foto. Prueba con otra.", Action.BACK_TO_NEW_VEHICLE),
    IMAGE_TOO_LARGE("La foto pesa más de 10 MB.", Action.BACK_TO_NEW_VEHICLE),
    UNSUPPORTED_MEDIA_TYPE("Solo se aceptan fotos JPG o PNG.", Action.BACK_TO_NEW_VEHICLE),
    PROVIDER_REJECTED("La IA no pudo procesar esta foto. Prueba con otra donde el carro se vea completo.",
            Action.BACK_TO_NEW_VEHICLE),
    INSUFFICIENT_CREDITS("Te quedaste sin créditos. Elige un plan para seguir creando modelos.", Action.SHOW_PLANS),
    QUOTA_EXCEEDED("Alcanzaste el máximo de modelos de hoy desde esta red. Podrás crear más mañana.",
            Action.QUOTA_EXHAUSTED),
    PROVIDER_ERROR("El servicio de IA falló. No se descontaron tus créditos.", Action.RETRY),
    SERVICE_BUSY("El servidor está ocupado. Intenta en {retryAfter} s.", Action.RETRY_COUNTDOWN),
    SERVICE_UNAVAILABLE("El servicio no está disponible en este momento.", Action.BACK_TO_GARAGE),
    PROVIDER_TIMEOUT("La IA tardó demasiado. No se descontaron tus créditos.", Action.RETRY),
    INTERNAL_ERROR("Ocurrió un error inesperado.", Action.BACK_TO_GARAGE),
    BAD_REQUEST("Ocurrió un error inesperado.", Action.BACK_TO_GARAGE, true),
    NOT_FOUND("Ocurrió un error inesperado.", Action.BACK_TO_GARAGE, true),
    METHOD_NOT_ALLOWED("Ocurrió un error inesperado.", Action.BACK_TO_GARAGE, true),

    // Cliente
    CLIENT_TIMEOUT("No hubo respuesta del servidor. Revisa tu conexión.", Action.BACK_TO_GARAGE),
    NETWORK_ERROR("Sin conexión con el servidor.", Action.BACK_TO_GARAGE),
    UNSUPPORTED_MODEL("El modelo recibido no es compatible con el visor.", Action.BACK_TO_GARAGE, true),
    NOT_A_VEHICLE("No detectamos un vehículo en la foto. Prueba con otra donde el carro se vea completo.",
            Action.STAY),
    CLASSIFIER_UNAVAILABLE("No pudimos revisar la foto. Reinicia la app.", Action.STAY, true);

    /** Qué hace la UI después de mostrar el mensaje. */
    public enum Action {
        /** Volver a Nuevo vehículo para elegir otra foto. */
        BACK_TO_NEW_VEHICLE,
        /** Volver al garaje. */
        BACK_TO_GARAGE,
        /** Ofrecer {@code Reintentar} manual (nunca automático). */
        RETRY,
        /** Ofrecer {@code Reintentar} habilitado tras la cuenta regresiva de {@code Retry-After}. */
        RETRY_COUNTDOWN,
        /** Marcar la cuota en cero y deshabilitar {@code + Nuevo vehículo}. */
        QUOTA_EXHAUSTED,
        /** Sin créditos: dejar el saldo en cero y mostrar los planes en vez del mensaje. */
        SHOW_PLANS,
        /** Regenerar el installation-id y volver al garaje. */
        REGENERATE_ID,
        /** Quedarse en la pantalla actual. */
        STAY
    }

    private final String message;
    private final Action action;
    /** Errores de programación o de compatibilidad que se registran en el log. */
    private final boolean logged;

    ErrorCode(String message, Action action) {
        this(message, action, false);
    }

    ErrorCode(String message, Action action, boolean logged) {
        this.message = message;
        this.action = action;
        this.logged = logged;
    }

    public Action action() {
        return action;
    }

    public boolean logged() {
        return logged;
    }

    public String message(Integer retryAfterSeconds) {
        return message.replace("{retryAfter}", retryAfterSeconds != null ? retryAfterSeconds.toString() : "unos");
    }

    public String message() {
        return message(null);
    }

    /** Traduce el {@code code} del backend; los desconocidos se tratan como {@code INTERNAL_ERROR}. */
    public static ErrorCode fromBackend(String code) {
        if (code == null) {
            return INTERNAL_ERROR;
        }
        return Arrays.stream(values())
                .filter(c -> c.name().equals(code))
                .findFirst()
                .orElse(INTERNAL_ERROR);
    }
}
