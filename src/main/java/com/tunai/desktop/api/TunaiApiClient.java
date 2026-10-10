package com.tunai.desktop.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.tunai.desktop.config.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente del backend (§6). Las llamadas son bloqueantes: se ejecutan fuera del hilo de la UI.
 * La generación <b>nunca</b> se reintenta automáticamente.
 */
public class TunaiApiClient {

    private static final Logger LOG = Logger.getLogger(TunaiApiClient.class.getName());

    public static final Duration GENERATION_TIMEOUT = Duration.ofSeconds(120);
    /** El backend en Render tarda ~1 minuto en despertar. */
    public static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(90);
    public static final Duration QUOTA_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;
    private final URI baseUrl;
    private final Supplier<UUID> installationId;
    private final String clientVersion;
    private final Duration generationTimeout;

    public TunaiApiClient(URI baseUrl, Supplier<UUID> installationId, String clientVersion) {
        this(baseUrl, installationId, clientVersion, GENERATION_TIMEOUT);
    }

    TunaiApiClient(URI baseUrl, Supplier<UUID> installationId, String clientVersion, Duration generationTimeout) {
        this.generationTimeout = generationTimeout;
        this.baseUrl = baseUrl;
        this.installationId = installationId;
        this.clientVersion = clientVersion;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public URI baseUrl() {
        return baseUrl;
    }

    /** {@code GET /actuator/health}: {@code true} con {@code 200}. */
    public boolean health() {
        return health(HEALTH_TIMEOUT);
    }

    public boolean health(Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("/actuator/health"))
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** {@code GET /api/v1/quota}. */
    public Quota quota() throws ApiException {
        HttpRequest request = apiRequest("/api/v1/quota", QUOTA_TIMEOUT).GET().build();
        HttpResponse<byte[]> response = send(request, QUOTA_TIMEOUT, null);
        if (response.statusCode() != 200) {
            throw toApiException(response);
        }
        try {
            JsonNode body = Json.MAPPER.readTree(response.body());
            return new Quota(body.path("limit").asInt(), body.path("remaining").asInt(),
                    parseInstant(body.path("resetsAt").asText(null)));
        } catch (IOException e) {
            throw ApiException.client(ErrorCode.INTERNAL_ERROR, "Respuesta de cuota ilegible", e);
        }
    }

    /**
     * {@code POST /api/v1/generations} (multipart, campo {@code image}). Timeout total de 120 s.
     *
     * @param cancel se completa para dejar de esperar; el backend puede terminar igual
     */
    public GenerationResult generate(byte[] image, String fileName, String mimeType,
                                     CompletableFuture<Void> cancel) throws ApiException {
        String boundary = "----TunAI" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(boundary, image, fileName, mimeType);
        HttpRequest request = apiRequest("/api/v1/generations", generationTimeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "model/gltf-binary, application/problem+json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> response = send(request, generationTimeout, cancel);
        if (response.statusCode() != 200) {
            throw toApiException(response);
        }
        return new GenerationResult(response.body(), quotaFromHeaders(response).orElse(null));
    }

    private HttpRequest.Builder apiRequest(String path, Duration timeout) {
        return HttpRequest.newBuilder(baseUrl.resolve(path))
                .timeout(timeout)
                .header("X-Installation-Id", installationId.get().toString())
                .header("X-Client-Version", clientVersion);
    }

    /** Envía con un límite de tiempo total (incluye la descarga del cuerpo). */
    private HttpResponse<byte[]> send(HttpRequest request, Duration totalTimeout, CompletableFuture<Void> cancel)
            throws ApiException {
        CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        if (cancel != null) {
            cancel.thenRun(() -> future.cancel(true));
        }
        try {
            return future.get(totalTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw ApiException.client(ErrorCode.CLIENT_TIMEOUT, request.method() + " " + request.uri(), e);
        } catch (CancellationException e) {
            throw new CancelledException();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CancelledException();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException)) {
                throw ApiException.client(ErrorCode.CLIENT_TIMEOUT, request.method() + " " + request.uri(), cause);
            }
            if (cancel != null && cancel.isDone()) {
                throw new CancelledException();
            }
            LOG.log(Level.INFO, "Error de red en " + request.uri() + ": " + cause);
            throw ApiException.client(ErrorCode.NETWORK_ERROR, String.valueOf(cause), cause);
        }
    }

    static ApiException toApiException(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        Integer retryAfter = response.headers().firstValue("Retry-After").flatMap(TunaiApiClient::parseInt).orElse(null);
        Quota quota = quotaFromHeaders(response).orElse(null);

        String rawCode = null;
        String traceId = null;
        String detail = null;
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (contentType.contains("json") && response.body() != null && response.body().length > 0) {
            try {
                JsonNode problem = Json.MAPPER.readTree(response.body());
                rawCode = problem.path("code").asText(null);
                traceId = problem.path("traceId").asText(null);
                detail = problem.path("detail").asText(null);
            } catch (IOException e) {
                LOG.fine("Cuerpo de error no es JSON válido");
            }
        }
        if (traceId == null) {
            traceId = response.headers().firstValue("X-Trace-Id").orElse(null);
        }

        ErrorCode code;
        if (rawCode != null) {
            code = ErrorCode.fromBackend(rawCode);
        } else {
            // Respuesta sin problem+json (p. ej. proxy de Render mientras despierta).
            code = status == 503 ? ErrorCode.SERVICE_UNAVAILABLE : ErrorCode.INTERNAL_ERROR;
        }
        ApiException ex = new ApiException(code, rawCode, status, traceId, retryAfter, quota, detail, null);
        if (code.logged() || code == ErrorCode.INTERNAL_ERROR) {
            LOG.warning("Error del backend: " + ex.getMessage() + " traceId=" + traceId);
        }
        return ex;
    }

    static Optional<Quota> quotaFromHeaders(HttpResponse<?> response) {
        Optional<Integer> limit = response.headers().firstValue("X-Quota-Limit").flatMap(TunaiApiClient::parseInt);
        Optional<Integer> remaining = response.headers().firstValue("X-Quota-Remaining").flatMap(TunaiApiClient::parseInt);
        if (limit.isEmpty() || remaining.isEmpty()) {
            return Optional.empty();
        }
        Instant reset = response.headers().firstValue("X-Quota-Reset").map(TunaiApiClient::parseInstant).orElse(null);
        return Optional.of(new Quota(limit.get(), remaining.get(), reset));
    }

    /** Acepta ISO-8601 o segundos desde epoch. */
    static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException e) {
            return parseLong(value).map(Instant::ofEpochSecond).orElse(null);
        }
    }

    private static Optional<Integer> parseInt(String value) {
        try {
            return Optional.of(Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value.strip()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static byte[] multipart(String boundary, byte[] content, String fileName, String mimeType) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length + 512);
        String safeName = fileName.replaceAll("[\"\\r\\n]", "_");
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: " + mimeType + "\r\n\r\n";
        out.writeBytes(head.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(content);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** El usuario dejó de esperar (§5.4, {@code Cancelar espera}). */
    public static class CancelledException extends ApiException {
        public CancelledException() {
            super(ErrorCode.CLIENT_TIMEOUT, "CANCELLED", 0, null, null, null, "Cancelado por el usuario", null);
        }
    }
}
