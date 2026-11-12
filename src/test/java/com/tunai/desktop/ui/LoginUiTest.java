package com.tunai.desktop.ui;

import com.sun.net.httpserver.HttpServer;
import com.tunai.desktop.App;
import javafx.application.Application;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inicio de sesión: primera pantalla sin sesión, validaciones, rechazo, acceso, sesión recordada y cierre. */
class LoginUiTest extends FxRobot {

    @TempDir
    Path home;

    private HttpServer server;
    private final AtomicInteger loginCalls = new AtomicInteger();
    private Application app;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/actuator/health", ex -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/api/v1/auth/login", ex -> {
            loginCalls.incrementAndGet();
            String request = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean ok = request.contains("\"password\":\"clave-buena\"");
            byte[] body = (ok
                    ? "{\"username\":\"demo\",\"token\":\"tok.en.firma\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}"
                    : "{\"status\":401,\"code\":\"INVALID_CREDENTIALS\",\"traceId\":\"t1\"}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", ok ? "application/json" : "application/problem+json");
            ex.sendResponseHeaders(ok ? 200 : 401, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/api/v1/quota", ex -> {
            byte[] body = ("{\"limit\":3,\"remaining\":3,\"resetsAt\":\"2099-01-01T00:00:00Z\","
                    + "\"credits\":45,\"creditsPerModel\":15}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        System.setProperty("tunai.home", home.toString());
        System.setProperty("tunai.api.url", "http://127.0.0.1:" + server.getAddress().getPort());
        Files.writeString(home.resolve("settings.json"),
                "{\"consentAcceptedAt\":\"2026-10-09T12:00:00Z\",\"classifier\":{\"enabled\":false}}");
        FxToolkit.registerPrimaryStage();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (app != null) {
            FxToolkit.cleanupApplication(app);
        }
        FxToolkit.hideStage();
        server.stop(0);
    }

    @Test
    void loginGatesTheAppAndRemembersTheSession() throws Exception {
        app = FxToolkit.setupApplication(App.class);

        // Sin sesión: la primera pantalla es el inicio de sesión
        assertTrue(lookup("Bienvenido").tryQuery().isPresent());
        assertTrue(lookup("Ingresa tus credenciales para continuar").tryQuery().isPresent());

        // Primera ventana del proceso: Windows puede gastar clics en activarla (ver FirstLaunchUiTest)
        TextField username = lookup("#usernameField").queryAs(TextField.class);
        for (int attempt = 0; attempt < 3 && !username.isFocused(); attempt++) {
            clickOn(username);
            WaitForAsyncUtils.waitForFxEvents();
        }
        assertTrue(username.isFocused());

        // Campos vacíos: no se llama al backend
        clickOn("Iniciar sesión");
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Ingresa tu correo o usuario.", lookup("#errorLabel").queryAs(Label.class).getText());
        assertEquals(0, loginCalls.get());

        // Contraseña vacía
        clickOn("#usernameField").write("demo");
        clickOn("Iniciar sesión");
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Ingresa tu contraseña.", lookup("#errorLabel").queryAs(Label.class).getText());
        assertEquals(0, loginCalls.get());

        // Mostrar y ocultar la contraseña
        clickOn("#passwordField").write("clave-mala");
        PasswordField hidden = lookup("#passwordField").queryAs(PasswordField.class);
        TextField visible = lookup("#passwordVisibleField").queryAs(TextField.class);
        assertTrue(hidden.isVisible());
        assertFalse(visible.isVisible());
        clickOn("#toggleButton");
        assertTrue(visible.isVisible());
        assertEquals("clave-mala", visible.getText());
        clickOn("#toggleButton");
        assertTrue(hidden.isVisible());

        // Credenciales incorrectas: mensaje claro y se queda en el login
        clickOn("Iniciar sesión");
        waitFor(() -> lookup("#errorLabel").queryAs(Label.class).getText().startsWith("Usuario o contraseña"));
        assertEquals(1, loginCalls.get());
        assertTrue(lookup("Bienvenido").tryQuery().isPresent());
        assertFalse(Files.readString(home.resolve("settings.json")).contains("tok.en.firma"));

        // Credenciales correctas: entra al garaje y guarda el token (no la contraseña)
        clickOn("#passwordField").push(KeyCode.CONTROL, KeyCode.A).write("clave-buena");
        clickOn("Iniciar sesión");
        waitFor(() -> lookup("Mi garaje").tryQuery().isPresent());
        String saved = Files.readString(home.resolve("settings.json"));
        assertTrue(saved.contains("tok.en.firma"));
        assertFalse(saved.contains("clave-buena"));

        // Con sesión vigente no se vuelven a pedir las credenciales
        FxToolkit.cleanupApplication(app);
        app = FxToolkit.setupApplication(App.class);
        assertTrue(lookup("Mi garaje").tryQuery().isPresent());
        assertTrue(lookup("Bienvenido").tryQuery().isEmpty());
    }

    @Test
    void signingOutReturnsToLoginAndDropsTheToken() throws Exception {
        Files.writeString(home.resolve("settings.json"),
                "{\"consentAcceptedAt\":\"2026-10-09T12:00:00Z\",\"account\":\"demo\",\"session\":{\"username\":\"demo\","
                        + "\"token\":\"tok.en.firma\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}}");
        app = FxToolkit.setupApplication(App.class);
        assertTrue(lookup("Mi garaje").tryQuery().isPresent());

        MenuButton menu = lookup(".help-menu").queryAs(MenuButton.class);
        interact(() -> menu.getItems().stream()
                .filter(item -> "signOutItem".equals(item.getId()))
                .findFirst().orElseThrow().fire());
        waitFor(() -> lookup("Bienvenido").tryQuery().isPresent());
        assertFalse(Files.readString(home.resolve("settings.json")).contains("tok.en.firma"));
        assertEquals("demo", lookup("#usernameField").queryAs(TextField.class).getText());
    }

    private void waitFor(Callable<Boolean> condition) throws Exception {
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, condition);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
