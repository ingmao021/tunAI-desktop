package com.tunai.desktop.ui;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkeletonTest {

    @BeforeAll
    static void toolkit() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    @Test
    void blockHasFixedSizeAndStyle() throws Exception {
        Skeleton s = WaitForAsyncUtils.asyncFx(() -> Skeleton.block(200, 120)).get(5, TimeUnit.SECONDS);
        assertEquals(200, s.getPrefWidth());
        assertEquals(120, s.getMaxHeight());
        assertTrue(s.getStyleClass().contains("skeleton"));
    }

    @Test
    void lineIsTwelvePixelsTall() throws Exception {
        Skeleton s = WaitForAsyncUtils.asyncFx(() -> Skeleton.line(80)).get(5, TimeUnit.SECONDS);
        assertEquals(12, s.getPrefHeight());
        assertTrue(s.getStyleClass().contains("skeleton-line"));
    }

    @Test
    void pulsesOnlyWhileOnScreen() throws Exception {
        Skeleton s = WaitForAsyncUtils.asyncFx(() -> Skeleton.block(50, 50)).get(5, TimeUnit.SECONDS);
        StackPane root = new StackPane();
        WaitForAsyncUtils.asyncFx(() -> {
            root.getChildren().add(s);
            new Scene(root, 100, 100);
        }).get(5, TimeUnit.SECONDS);
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        assertTrue(s.getOpacity() < 1.0, "Pulsando con la escena puesta");

        WaitForAsyncUtils.asyncFx(() -> root.getChildren().clear()).get(5, TimeUnit.SECONDS);
        WaitForAsyncUtils.sleep(100, TimeUnit.MILLISECONDS);
        double stopped = s.getOpacity();
        WaitForAsyncUtils.sleep(400, TimeUnit.MILLISECONDS);
        assertEquals(stopped, s.getOpacity(), 1e-9, "Sin escena la animación se detiene");
    }
}
