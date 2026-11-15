package com.tunai.desktop.api;

/** Error de una llamada al backend, o de la app, listo para mostrar con su {@link ErrorCode}. */
public class ApiException extends Exception {

    private final ErrorCode code;
    /** Código tal como llegó del backend (puede no estar en el catálogo). */
    private final String rawCode;
    private final int httpStatus;
    private final String traceId;
    private final Integer retryAfterSeconds;
    /** Cuota que venía en los headers del error, si había. */
    private final Quota quota;
    /**
     * El servidor declaró que la generación sigue en curso y que repetir la petición con la misma foto la retoma sin
     * crear (ni cobrar) otro modelo. Solo entonces es seguro reintentar sin que el usuario lo pida.
     */
    private final boolean resumable;

    public ApiException(ErrorCode code, String rawCode, int httpStatus, String traceId,
                        Integer retryAfterSeconds, Quota quota, String detail, Throwable cause) {
        this(code, rawCode, httpStatus, traceId, retryAfterSeconds, quota, detail, false, cause);
    }

    public ApiException(ErrorCode code, String rawCode, int httpStatus, String traceId,
                        Integer retryAfterSeconds, Quota quota, String detail, boolean resumable, Throwable cause) {
        super(code + (rawCode != null && !rawCode.equals(code.name()) ? " (" + rawCode + ")" : "")
                + (httpStatus > 0 ? " HTTP " + httpStatus : "")
                + (detail != null ? ": " + detail : ""), cause);
        this.code = code;
        this.rawCode = rawCode;
        this.httpStatus = httpStatus;
        this.traceId = traceId;
        this.retryAfterSeconds = retryAfterSeconds;
        this.quota = quota;
        this.resumable = resumable;
    }

    public static ApiException client(ErrorCode code, String detail, Throwable cause) {
        return new ApiException(code, code.name(), 0, null, null, null, detail, cause);
    }

    public ErrorCode code() {
        return code;
    }

    public String rawCode() {
        return rawCode;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String traceId() {
        return traceId;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Quota quota() {
        return quota;
    }

    public boolean resumable() {
        return resumable;
    }

    public String userMessage() {
        return code.message(retryAfterSeconds);
    }
}
