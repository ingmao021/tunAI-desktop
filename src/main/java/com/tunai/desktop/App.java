package com.tunai.desktop;

import com.tunai.desktop.config.AppPaths;
import com.tunai.desktop.ui.Navigator;
import com.tunai.desktop.ui.Resources;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

public class App extends Application {

    public static final String NAME = "TunAI";
    public static final double MIN_WIDTH = 1280;
    public static final double MIN_HEIGHT = 720;

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    private AppContext context;

    @Override
    public void init() {
        AppPaths paths = AppPaths.fromEnvironment();
        LoggingSetup.configure(paths.logsDir());
        LOG.info("Iniciando " + NAME + " " + version());
        context = new AppContext(paths);
        LOG.info("Backend: " + context.api().baseUrl());
    }

    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(new StackPane(), MIN_WIDTH, MIN_HEIGHT);
        scene.getStylesheets().add(Resources.stylesheet());
        Navigator navigator = new Navigator(scene, context);
        context.attach(stage, navigator);

        stage.setTitle(NAME);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (!navigator.canLeaveCurrent()) {
                e.consume();
            }
        });

        // Calentamiento: despierta el backend de inmediato; el garaje funciona mientras tanto.
        context.serverMonitor().start();
        context.background().execute(context.classifier()::load);
        navigator.goTo(context.settings().get().hasConsent() ? Navigator.Screen.GARAGE : Navigator.Screen.CONSENT);
        stage.show();
    }

    @Override
    public void stop() {
        context.serverMonitor().stop();
        context.background().shutdownNow();
        context.classifier().close();
        if (context.navigator() != null) {
            context.navigator().leaveCurrent();
        }
        LoggingSetup.shutdown();
    }

    public AppContext context() {
        return context;
    }

    public static String version() {
        try (InputStream in = App.class.getResourceAsStream("/com/tunai/desktop/app.properties")) {
            Properties props = new Properties();
            if (in != null) {
                props.load(in);
            }
            return props.getProperty("version", "dev");
        } catch (IOException e) {
            return "dev";
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
