package com.tunai.desktop.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunaiApiClientTest {

    private static final UUID INSTALLATION = UUID.fromString("3f2b8c1e-8d4a-4f6e-9b1a-2c3d4e5f6a7b");

    private HttpServer server;
    private TunaiApiClient client;
    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();
    private final AtomicInteger generationCalls = new AtomicInteger();
    private final AtomicReference<HttpExchangeSnapshot> lastGeneration = new AtomicReference<>();

    interface Handler {
        void handle(HttpExchange ex) throws IOException;
    }

    record HttpExchangeSnapshot(String installationId, String clientVersion, String contentType, String resume,
                                byte[] body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            if (ex.getRequestURI().getPath().equals("/api/v1/generations")) {
                generationCalls.incrementAndGet();
                lastGeneration.set(new HttpExchangeSnapshot(
                        ex.getRequestHeaders().getFirst("X-Installation-Id"),
                        ex.getRequestHeaders().getFirst("X-Client-Version"),
                        ex.getRequestHeaders().getFirst("Content-Type"),
                        ex.getRequestHeaders().getFirst(TunaiApiClient.RESUME_HEADER),
                        ex.getRequestBody().readAllBytes()));
            }
            Handler h = handlers.get(ex.getRequestURI().getPath());
            if (h == null) {
                ex.sendResponseHeaders(404, -1);
            } else {
                h.handle(ex);
            }
            ex.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        client = new TunaiApiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                () -> INSTALLATION, "1.0.0-test", Duration.ofSeconds(2));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        if (contentType != null) {
            ex.getResponseHeaders().add("Content-Type", contentType);
        }
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        ex.getResponseBody().write(body);
    }

    private static void problem(HttpExchange ex, int status, String code) throws IOException {
        String json = "{\"type\":\"about:blank\",\"title\":\"x\",\"status\":" + status + ",\"code\":\"" + code
                + "\",\"detail\":\"texto que la UI no usa\",\"traceId\":\"trace-" + code + "\"}";
        respond(ex, status, "application/problem+json", json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void healthReflectsStatus() {
        handlers.put("/actuator/health", ex -> respond(ex, 200, "application/json", "{\"status\":\"UP\"}".getBytes()));
        assertTrue(client.health());
        handlers.put("/actuator/health", ex -> respond(ex, 503, null, new byte[0]));
        assertFalse(client.health());
    }

    @Test
    void healthFalseWhenServerDown() {
        server.stop(0);
        assertFalse(client.health(Duration.ofSeconds(2)));
    }

    @Test
    void quotaParsesBodyAndSendsInstallationId() throws Exception {
        AtomicReference<String> header = new AtomicReference<>();
        handlers.put("/api/v1/quota", ex -> {
            header.set(ex.getRequestHeaders().getFirst("X-Installation-Id"));
            respond(ex, 200, "application/json",
                    ("{\"limit\":3,\"remaining\":1,\"resetsAt\":\"2026-10-10T05:00:00Z\","
                            + "\"credits\":15,\"creditsPerModel\":15}").getBytes());
        });
        Quota quota = client.quota();
        assertEquals(new Quota(3, 1, Instant.parse("2026-10-10T05:00:00Z"), 15, 15), quota);
        assertEquals(2, quota.used());
        assertEquals(INSTALLATION.toString(), header.get());
    }

    @Test
    void generationReturnsGlbAndQuotaHeaders() throws Exception {
        byte[] glb = {'g', 'l', 'T', 'F', 2, 0, 0, 0, 12, 0, 0, 0};
        handlers.put("/api/v1/generations", ex -> {
            ex.getResponseHeaders().add("X-Quota-Limit", "2");
            ex.getResponseHeaders().add("X-Quota-Remaining", "0");
            ex.getResponseHeaders().add("X-Quota-Reset", "2026-10-11T05:00:00Z");
            ex.getResponseHeaders().add("X-Credits-Balance", "0");
            ex.getResponseHeaders().add("X-Credits-Per-Model", "20");
            respond(ex, 200, "model/gltf-binary", glb);
        });
        byte[] photo = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        GenerationResult result = client.generate(photo, "carro.jpg", "image/jpeg", new CompletableFuture<>());

        assertArrayEquals(glb, result.glb());
        assertEquals(new Quota(2, 0, Instant.parse("2026-10-11T05:00:00Z"), 0, 20), result.quota());

        HttpExchangeSnapshot sent = lastGeneration.get();
        assertEquals(INSTALLATION.toString(), sent.installationId());
        assertEquals("1.0.0-test", sent.clientVersion());
        assertTrue(sent.contentType().startsWith("multipart/form-data; boundary="));
        String body = new String(sent.body(), StandardCharsets.ISO_8859_1);
        assertTrue(body.contains("name=\"image\"; filename=\"carro.jpg\""));
        assertTrue(body.contains("Content-Type: image/jpeg"));
    }

    @ParameterizedTest
    @CsvSource({
            "400, INVALID_INSTALLATION_ID",
            "400, INVALID_IMAGE",
            "413, IMAGE_TOO_LARGE",
            "415, UNSUPPORTED_MEDIA_TYPE",
            "422, PROVIDER_REJECTED",
            "429, QUOTA_EXCEEDED",
            "502, PROVIDER_ERROR",
            "503, SERVICE_BUSY",
            "503, SERVICE_UNAVAILABLE",
            "504, PROVIDER_TIMEOUT",
            "500, INTERNAL_ERROR",
            "400, BAD_REQUEST",
            "404, NOT_FOUND",
            "405, METHOD_NOT_ALLOWED",
    })
    void everyBackendCodeMapsToCatalogAndIsNeverRetried(int status, String code) {
        handlers.put("/api/v1/generations", ex -> {
            ex.getResponseHeaders().add("Retry-After", "7");
            problem(ex, status, code);
        });
        ApiException ex = assertThrows(ApiException.class,
                () -> client.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>()));
        assertEquals(ErrorCode.valueOf(code), ex.code());
        assertEquals(status, ex.httpStatus());
        assertEquals("trace-" + code, ex.traceId());
        assertEquals(7, ex.retryAfterSeconds());
        assertEquals(1, generationCalls.get(), "La generación nunca se reintenta sola");
    }

    private static void timeoutProblem(HttpExchange ex, String extraJson) throws IOException {
        String json = "{\"status\":504,\"code\":\"PROVIDER_TIMEOUT\",\"traceId\":\"t1\"" + extraJson + "}";
        ex.getResponseHeaders().add("Retry-After", "20");
        respond(ex, 504, "application/problem+json", json.getBytes(StandardCharsets.UTF_8));
    }

    /** El servidor declara que el modelo sigue generándose: la UI puede retomar con la misma foto. */
    @Test
    void aTimeoutTheServerMarksResumableIsResumable() {
        handlers.put("/api/v1/generations", ex -> timeoutProblem(ex, ",\"resumable\":true"));
        ApiException ex = assertThrows(ApiException.class,
                () -> client.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>()));
        assertEquals(ErrorCode.PROVIDER_TIMEOUT, ex.code());
        assertTrue(ex.resumable());
        assertEquals(20, ex.retryAfterSeconds());
        assertEquals(1, generationCalls.get(), "El cliente nunca reintenta por su cuenta");
    }

    /** Un servidor sin la corrección no manda el flag: ahí reintentar crearía otra tarea cobrada. */
    @Test
    void aTimeoutWithoutTheFlagIsNotResumable() {
        handlers.put("/api/v1/generations", ex -> timeoutProblem(ex, ""));
        ApiException ex = assertThrows(ApiException.class,
                () -> client.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>()));
        assertFalse(ex.resumable());
    }

    @Test
    void theResumableFlagOnAnyOtherErrorIsIgnored() {
        handlers.put("/api/v1/generations", ex -> respond(ex, 502, "application/problem+json",
                "{\"status\":502,\"code\":\"PROVIDER_ERROR\",\"resumable\":true}".getBytes(StandardCharsets.UTF_8)));
        ApiException ex = assertThrows(ApiException.class,
                () -> client.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>()));
        assertFalse(ex.resumable());
    }

    /**
     * Solo la retoma automática lleva el header: con él el servidor nunca crea otra generación. La petición que
     * confirmó el usuario va sin él.
     */
    @Test
    void theResumeHeaderIsSentOnlyOnResumes() throws Exception {
        byte[] glb = {'g', 'l', 'T', 'F', 2, 0, 0, 0, 12, 0, 0, 0};
        handlers.put("/api/v1/generations", ex -> respond(ex, 200, "model/gltf-binary", glb));

        client.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>());
        assertNull(lastGeneration.get().resume(), "La generación que confirma el usuario no es una retoma");

        client.generate(new byte[]{1}, "a.png", "image/png", false, new CompletableFuture<>());
        assertNull(lastGeneration.get().resume());

        client.generate(new byte[]{1}, "a.png", "image/png", true, new CompletableFuture<>());
        assertEquals("true", lastGeneration.get().resume());
    }

    private TunaiApiClient clientWith(Duration headersTimeout, Duration totalTimeout) {
        return new TunaiApiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                () -> INSTALLATION, "1.0.0-test", headersTimeout, totalTimeout);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Un modelo que el servidor ya empezó a entregar está generado y cobrado: una descarga lenta no lo pierde aunque
     * supere el plazo de espera de la respuesta, mientras quepa en el plazo total.
     */
    @Test
    void aSlowDownloadHasTheTotalTimeoutNotTheResponseOne() throws Exception {
        byte[] glb = new byte[4096];
        glb[0] = 'g';
        handlers.put("/api/v1/generations", ex -> {
            ex.getResponseHeaders().add("Content-Type", "model/gltf-binary");
            ex.sendResponseHeaders(200, glb.length);
            ex.getResponseBody().write(glb, 0, 1024);
            ex.getResponseBody().flush();
            sleep(2000);
            ex.getResponseBody().write(glb, 1024, glb.length - 1024);
        });
        TunaiApiClient patient = clientWith(Duration.ofMillis(700), Duration.ofSeconds(8));

        GenerationResult result = patient.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>());

        assertArrayEquals(glb, result.glb());
    }

    /** Si el servidor no empieza a responder en su plazo, no se espera el plazo total. */
    @Test
    void aServerThatDoesNotAnswerInTimeIsAClientTimeoutBeforeTheTotal() {
        handlers.put("/api/v1/generations", ex -> sleep(4000));
        TunaiApiClient impatient = clientWith(Duration.ofMillis(700), Duration.ofSeconds(8));
        long startedAt = System.nanoTime();

        ApiException ex = assertThrows(ApiException.class,
                () -> impatient.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>()));

        assertEquals(ErrorCode.CLIENT_TIMEOUT, ex.code());
        assertTrue(Duration.ofNanos(System.nanoTime() - startedAt).compareTo(Duration.ofSeconds(3)) < 0,
                "Debe rendirse al vencer el plazo de respuesta, no el total");
    }

    /** La descarga también tiene límite: el plazo total. */
    @Test
    void aDownloadThatNeverEndsIsAClientTimeoutAtTheTotal() {
        handlers.put("/api/v1/generations", ex -> {
            ex.getResponseHeaders().add("Content-Type", "model/gltf-binary");
            ex.sendResponseHeaders(200, 4096);
            ex.getResponseBody().write(new byte[1024]);
            ex.getResponseBody().flush();
            sleep(5000);
        });
        TunaiApiClient bounded = clientWith(Duration.ofMillis(500), Duration.ofMillis(1500));

        ApiException ex = assertThrows(ApiException.class,
                () -> bounded.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>()));

        assertEquals(ErrorCode.CLIENT_TIMEOUT, ex.code());
    }

    @Test
    void unknownCodeIsTreatedAsInternalError() {
        handlers.put("/api/v1/generations", ex -> problem(ex, 418, "CODIGO_NUEVO"));
        ApiException ex = assertThrows(ApiException.class,
                () -> client.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>()));
        assertEquals(ErrorCode.INTERNAL_ERROR, ex.code());
        assertEquals("CODIGO_NUEVO", ex.rawCode());
    }

    @Test
    void serviceBusyMessageUsesRetryAfter() {
        handlers.put("/api/v1/generations", ex -> {
            ex.getResponseHeaders().add("Retry-After", "30");
            problem(ex, 503, "SERVICE_BUSY");
        });
        ApiException ex = assertThrows(ApiException.class,
                () -> client.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>()));
        assertEquals("El servidor está ocupado. Intenta en 30 s.", ex.userMessage());
    }

    @Test
    void nonJsonErrorFallsBackByStatus() {
        handlers.put("/api/v1/generations", ex -> respond(ex, 503, "text/html", "<html>".getBytes()));
        ApiException ex = assertThrows(ApiException.class,
                () -> client.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>()));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.code());
    }

    @Test
    void slowServerGivesClientTimeout() {
        handlers.put("/api/v1/generations", ex -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        ApiException ex = assertThrows(ApiException.class,
                () -> client.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>()));
        assertEquals(ErrorCode.CLIENT_TIMEOUT, ex.code());
        assertEquals(1, generationCalls.get());
    }

    @Test
    void serverDownGivesNetworkError() {
        server.stop(0);
        ApiException ex = assertThrows(ApiException.class,
                () -> client.generate(new byte[]{1}, "a.png", "image/png", new CompletableFuture<>()));
        assertEquals(ErrorCode.NETWORK_ERROR, ex.code());
    }

    @Test
    void cancelStopsWaiting() {
        handlers.put("/api/v1/generations", ex -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        CompletableFuture<Void> cancel = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(300, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> cancel.complete(null));
        ApiException ex = assertThrows(ApiException.class,
                () -> client.generate(new byte[]{1}, "a.png", "image/png", cancel));
        assertInstanceOf(TunaiApiClient.CancelledException.class, ex);
    }

    @Test
    void parsesResetAsEpochSeconds() {
        assertEquals(Instant.ofEpochSecond(1_760_072_400L), TunaiApiClient.parseInstant("1760072400"));
    }
}
