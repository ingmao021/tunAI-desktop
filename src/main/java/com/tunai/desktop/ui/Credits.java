package com.tunai.desktop.ui;

import com.tunai.desktop.api.Quota;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Créditos de la instalación: se reciben {@value #INITIAL} al instalar y cada modelo 3D usa lo que diga el backend
 * ({@code creditsPerModel}); {@value #DEFAULT_PER_MODEL} hasta su primera respuesta. Al acabarse se ofrecen los planes
 * de precios ({@link PricingDialog}). El límite diario de la red del backend aplica aparte.
 */
public final class Credits {

    public static final int INITIAL = 45;
    public static final int DEFAULT_PER_MODEL = 15;

    private static final DateTimeFormatter RESET_TIME =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private Credits() {
    }

    public static boolean canGenerate(int credits, int perModel) {
        return credits >= perModel;
    }

    public static int models(int credits, int perModel) {
        return Math.max(0, credits) / perModel;
    }

    /**
     * Quedan créditos pero el backend no deja generar más hoy (límite diario de la red). Sin créditos, la cuota también
     * llega en cero, pero entonces se muestran los planes, no este aviso.
     */
    static boolean dailyLimitReached(Quota quota, int credits, int perModel) {
        return quota != null && quota.exhausted() && canGenerate(credits, perModel);
    }

    static String dailyLimitMessage(Quota quota) {
        String when = quota != null && quota.resetsAt() != null
                ? "a las " + RESET_TIME.format(quota.resetsAt()) : "mañana";
        return "Alcanzaste el máximo de modelos de hoy desde esta red. Podrás crear más " + when + ".";
    }

    /** Contador del encabezado, acompañado de {@link #icon(double)}. */
    static String header(int credits) {
        return credits == 1 ? "1 crédito" : credits + " créditos";
    }

    static String generationWarning(int credits, int perModel) {
        if (!canGenerate(credits, perModel)) {
            return "Tienes " + header(credits) + " y cada modelo usa " + perModel + ". Elige un plan para seguir.";
        }
        int left = credits - perModel;
        int more = models(left, perModel);
        return "Tienes " + header(credits) + ". Este modelo usa " + perModel + " y te quedarán " + left
                + " (" + (more == 1 ? "1 modelo más" : more + " modelos más") + ").";
    }

    static String confirmMessage(int perModel) {
        return "Vas a usar " + perModel + " créditos. ¿Continuar?";
    }

    /** Moneda dorada que acompaña a las cantidades de créditos. */
    static Node icon(double size) {
        Circle coin = new Circle(size / 2);
        coin.getStyleClass().add("credit-coin");
        Label mark = new Label("C");
        mark.getStyleClass().add("credit-coin-mark");
        mark.setStyle("-fx-font-size: " + Math.round(size * 0.62) + "px;");
        StackPane icon = new StackPane(coin, mark);
        icon.setMinSize(size, size);
        icon.setMaxSize(size, size);
        return icon;
    }
}
