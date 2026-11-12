package com.tunai.desktop.ui;

import com.tunai.desktop.AppContext;
import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.util.Duration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.util.function.Consumer;

/** Cambia la pantalla visible cargando su FXML. Los controladores reciben el {@link AppContext}. */
public class Navigator {

    public enum Screen {
        LOGIN("LoginView.fxml"),
        CONSENT("ConsentView.fxml"),
        GARAGE("GarageView.fxml"),
        NEW_VEHICLE("NewVehicleView.fxml"),
        GENERATING("GeneratingView.fxml"),
        VIEWER("ViewerView.fxml"),
        SETTINGS("SettingsView.fxml");

        private final String fxml;

        Screen(String fxml) {
            this.fxml = fxml;
        }
    }

    /** Primera pantalla con sesión válida: el consentimiento si aún no lo aceptó, si no el garaje. */
    public static Screen afterLogin(com.tunai.desktop.config.Settings settings) {
        return settings.hasConsent() ? Screen.GARAGE : Screen.CONSENT;
    }

    private static final Duration FADE_IN = Duration.millis(220);

    private final Scene scene;
    private final AppContext context;
    private Object currentController;

    public Navigator(Scene scene, AppContext context) {
        this.scene = scene;
        this.context = context;
    }

    public void goTo(Screen screen) {
        goTo(screen, controller -> {
        });
    }

    /**
     * Muestra {@code screen}. {@code init} recibe el controlador antes de mostrarlo.
     *
     * @return {@code false} si la pantalla actual canceló la navegación
     */
    @SuppressWarnings("unchecked")
    public <C> boolean goTo(Screen screen, Consumer<C> init) {
        if (!canLeaveCurrent()) {
            return false;
        }
        leaveCurrent();
        FXMLLoader loader = new FXMLLoader(Navigator.class.getResource("/com/tunai/desktop/ui/" + screen.fxml));
        loader.setControllerFactory(this::createController);
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar " + screen.fxml, e);
        }
        C controller = loader.getController();
        init.accept(controller);
        currentController = controller;
        Parent shown = SidebarShell.wraps(screen) ? new SidebarShell(root, screen, context) : root;
        scene.setRoot(shown);
        FadeTransition fade = new FadeTransition(FADE_IN, root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
        return true;
    }

    public boolean canLeaveCurrent() {
        return !(currentController instanceof NavigationAware aware) || aware.canLeave();
    }

    public void leaveCurrent() {
        if (currentController instanceof NavigationAware aware) {
            aware.onLeave();
        }
        currentController = null;
    }

    public Object currentController() {
        return currentController;
    }

    public Scene scene() {
        return scene;
    }

    private Object createController(Class<?> type) {
        try {
            for (Constructor<?> c : type.getConstructors()) {
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == AppContext.class) {
                    return c.newInstance(context);
                }
            }
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo crear el controlador " + type.getName(), e);
        }
    }
}
