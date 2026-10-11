package com.tunai.desktop.ui;

import com.sun.net.httpserver.HttpServer;
import com.tunai.desktop.App;
import com.tunai.desktop.AppContext;
import com.tunai.desktop.garage.GarageVehicle;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flujo completo contra un backend falso: CA-UI-03, 05, 06, 07, 08, 09, 10 y 12.
 * Guarda capturas de cada pantalla en {@code target/ui-snapshots}.
 */
class GenerationFlowUiTest extends FxRobot {

    @TempDir
    Path home;

    private HttpServer server;
    private final AtomicInteger generationCalls = new AtomicInteger();
    private final AtomicReference<String> generationMode = new AtomicReference<>("ok");
    private final AtomicInteger remaining = new AtomicInteger(2);
    private Application app;
    private byte[] glb;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/glb/BoxTextured.glb")) {
            glb = in.readAllBytes();
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/actuator/health", ex -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes();
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/api/v1/quota", ex -> {
            byte[] body = ("{\"limit\":2,\"remaining\":" + remaining.get() + ",\"resetsAt\":\"2026-10-11T05:00:00Z\"}").getBytes();
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/api/v1/generations", ex -> {
            ex.getRequestBody().readAllBytes();
            generationCalls.incrementAndGet();
            if (generationMode.get().equals("ok")) {
                int left = remaining.decrementAndGet();
                ex.getResponseHeaders().add("Content-Type", "model/gltf-binary");
                ex.getResponseHeaders().add("X-Quota-Limit", "2");
                ex.getResponseHeaders().add("X-Quota-Remaining", String.valueOf(left));
                ex.getResponseHeaders().add("X-Quota-Reset", "2026-10-11T05:00:00Z");
                ex.sendResponseHeaders(200, glb.length);
                ex.getResponseBody().write(glb);
            } else {
                byte[] body = "{\"status\":502,\"code\":\"PROVIDER_ERROR\",\"traceId\":\"abc123\"}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/problem+json");
                ex.sendResponseHeaders(502, body.length);
                ex.getResponseBody().write(body);
            }
            ex.close();
        });
        server.start();

        System.setProperty("tunai.home", home.toString());
        System.setProperty("tunai.api.url", "http://127.0.0.1:" + server.getAddress().getPort());
        Files.writeString(home.resolve("settings.json"),
                "{\"consentAcceptedAt\":\"2026-10-09T12:00:00Z\",\"classifier\":{\"enabled\":false}}");
        FxToolkit.registerPrimaryStage();
        FxToolkit.setupStage(s -> s.setMaximized(false));
        launch();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (app != null) {
            FxToolkit.cleanupApplication(app);
        }
        FxToolkit.hideStage();
        server.stop(0);
        System.clearProperty("tunai.api.url");
    }

    private void launch() throws Exception {
        app = FxToolkit.setupApplication(App.class);
    }

    private AppContext context() {
        return ((App) app).context();
    }

    @Test
    void fullFlowGenerateRecolorSaveReopen() throws Exception {
        // CA-UI-03 y CA-UI-10: indicador del servidor y cuota inicial.
        waitFor(() -> lookup("●").tryQuery().isPresent());
        waitFor(() -> lookup("Modelos 3D hoy: 0 de 2").tryQuery().isPresent());
        snapshot("01-garaje-vacio.png");

        clickOn("+ Nuevo vehículo");
        NewVehicleController newVehicle = (NewVehicleController) context().navigator().currentController();
        Path photo = writePhoto("carro.jpg", Color.RED);
        interact(() -> newVehicle.selectFile(photo));
        // El nombre ya no viene relleno (solo placeholder): sin nombre no se puede generar.
        TextField nameField = lookup("#nameField").queryAs(TextField.class);
        assertTrue(nameField.getText().isEmpty());
        interact(() -> nameField.setText("Mi carro"));
        Button generate = lookup("Generar modelo").queryButton();
        waitFor(() -> !generate.isDisabled());
        snapshot("02-nuevo-vehiculo.png");

        // CA-UI-05: confirmación antes de generar.
        clickOn(generate);
        waitFor(() -> lookup("Vas a usar 1 de tus generaciones. ¿Continuar?").tryQuery().isPresent());
        clickOn("Generar");

        waitFor(() -> lookup("Personalizar").tryQuery().isPresent());
        waitFor(() -> lookup(".swatch").queryAll().stream().noneMatch(Node::isDisabled));
        assertEquals(1, generationCalls.get());
        assertEquals(1, context().session().getQuota().remaining(), "CA-UI-10: cuota de los headers");
        snapshot("03-visor.png");

        // CA-UI-08: recolorear sin congelar y restaurar.
        List<Node> swatches = List.copyOf(lookup(".swatch").queryAll());
        clickOn(swatches.get(1)); // Azul
        Label unsaved = lookup("· sin guardar").queryAs(Label.class);
        waitFor(unsaved::isVisible);
        waitFor(() -> !lookup(".progress-indicator").queryAll().stream()
                .filter(n -> n.getParent() != null && n.getParent().getStyleClass().isEmpty())
                .anyMatch(Node::isVisible));
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        snapshot("04-visor-azul.png");

        clickOn("Antes");
        WaitForAsyncUtils.waitForFxEvents();
        snapshot("05-visor-antes.png");
        clickOn("Después");

        clickOn("Guardar");
        waitFor(() -> !unsaved.isVisible());
        GarageVehicle saved = context().garage().list().get(0);
        assertTrue(saved.editedTexture().isPresent());
        assertTrue(saved.thumbnail().isPresent());
        assertEquals("#1565C0", saved.metadata().paint.color);

        clickOn("← Garaje");
        waitFor(() -> lookup("Mi carro").tryQuery().isPresent());
        waitFor(() -> lookup("Modelos 3D hoy: 1 de 2").tryQuery().isPresent());
        snapshot("06-garaje-con-vehiculo.png");

        // CA-UI-09: cerrar y reabrir conserva el vehículo con su color.
        FxToolkit.cleanupApplication(app);
        launch();
        clickOn("Mi carro");
        waitFor(() -> lookup(".swatch").queryAll().stream().noneMatch(Node::isDisabled));
        ViewerController viewer = (ViewerController) context().navigator().currentController();
        assertFalse(unsavedVisible(), "Reabrir no debe marcar cambios");
        assertTrue(lookup(".swatch").queryAll().stream()
                .anyMatch(n -> ((javafx.scene.control.ToggleButton) n).isSelected()
                        && "#1565C0".equals(n.getUserData())));
        assertTrue(viewer.canLeave());

        // Restaurar original marca cambios y "Salir sin guardar" no guarda.
        clickOn("Restaurar original");
        waitFor(this::unsavedVisible);
    }

    @Test
    void providerErrorShowsMessageAndNeverRetries() throws Exception {
        generationMode.set("error");
        waitFor(() -> lookup("Modelos 3D hoy: 0 de 2").tryQuery().isPresent());
        clickOn("+ Nuevo vehículo");
        NewVehicleController newVehicle = (NewVehicleController) context().navigator().currentController();
        Path photo = writePhoto("carro.png", Color.BLUE);
        interact(() -> newVehicle.selectFile(photo));
        interact(() -> lookup("#nameField").queryAs(TextField.class).setText("Error"));
        Button generate = lookup("Generar modelo").queryButton();
        waitFor(() -> !generate.isDisabled());
        clickOn(generate);
        clickOn("Generar");

        waitFor(() -> lookup("El servicio de IA falló. No se descontó tu cuota.").tryQuery().isPresent());
        clickOn("Detalles");
        waitFor(() -> lookup(n -> n instanceof Label l && l.getText() != null && l.getText().contains("abc123"))
                .tryQuery().isPresent());
        snapshotWindow("07-error-proveedor.png");
        WaitForAsyncUtils.sleep(1, TimeUnit.SECONDS);
        assertEquals(1, generationCalls.get(), "Nunca se reintenta solo");

        clickOn("Volver al garaje");
        waitFor(() -> lookup("Mi garaje").tryQuery().isPresent());
        assertEquals(1, generationCalls.get());
        assertTrue(context().garage().list().isEmpty());
    }

    /** CA-UI-12: una foto que no es un vehículo se rechaza sin llamar al backend. */
    @Test
    void nonVehiclePhotoBlocksGeneration() throws Exception {
        context().settings().get().classifier.enabled = true;
        waitFor(() -> lookup("Modelos 3D hoy: 0 de 2").tryQuery().isPresent());
        clickOn("+ Nuevo vehículo");
        NewVehicleController newVehicle = (NewVehicleController) context().navigator().currentController();
        Path photo = writePhoto("cuadro.jpg", Color.BLACK);
        interact(() -> newVehicle.selectFile(photo));
        waitFor(() -> lookup(n -> n instanceof Label l && l.getText() != null
                && l.getText().startsWith("✗ No detectamos un vehículo")).tryQuery().isPresent());
        snapshot("08-no-es-vehiculo.png");
        assertTrue(lookup("Generar modelo").queryButton().isDisabled());
        assertEquals(0, generationCalls.get());
    }

    private boolean unsavedVisible() {
        return lookup("· sin guardar").queryAs(Label.class).isVisible();
    }

    private Path writePhoto(String name, Color color) throws Exception {
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 800, 600);
        g.dispose();
        Path file = home.resolve(name);
        ImageIO.write(img, name.endsWith(".png") ? "png" : "jpg", file.toFile());
        return file;
    }

    private static void waitFor(Callable<Boolean> condition) throws Exception {
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, condition);
    }

    private void snapshot(String name) throws Exception {
        AtomicReference<WritableImage> image = new AtomicReference<>();
        interact(() -> image.set(context().stage().getScene().snapshot(null)));
        save(image.get(), name);
    }

    /** Captura la ventana activa (por ejemplo un diálogo). */
    private void snapshotWindow(String name) throws Exception {
        AtomicReference<WritableImage> image = new AtomicReference<>();
        interact(() -> image.set(listTargetWindows().get(listTargetWindows().size() - 1).getScene().snapshot(null)));
        save(image.get(), name);
    }

    private static void save(WritableImage image, String name) throws Exception {
        Path dir = Files.createDirectories(Path.of("target", "ui-snapshots"));
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", dir.resolve(name).toFile());
    }
}
