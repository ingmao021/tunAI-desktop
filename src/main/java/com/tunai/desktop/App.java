package com.tunai.desktop;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {

    public static final String NAME = "TunAI";
    public static final double MIN_WIDTH = 1280;
    public static final double MIN_HEIGHT = 720;

    @Override
    public void start(Stage stage) {
        StackPane root = new StackPane(new Label("TunAI"));
        Scene scene = new Scene(root, MIN_WIDTH, MIN_HEIGHT);
        scene.getStylesheets().add(App.class.getResource("/com/tunai/desktop/styles/app.css").toExternalForm());

        stage.setTitle(NAME);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
