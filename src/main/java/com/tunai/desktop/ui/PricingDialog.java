package com.tunai.desktop.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

/** Planes de créditos que se ofrecen al quedarse sin créditos. Precios de ejemplo para la demo. */
final class PricingDialog {

    private record Plan(String name, int credits, String price, boolean featured) {
    }

    private static final List<Plan> PLANS = List.of(
            new Plan("Básico", 75, "US$ 4,99", false),
            new Plan("Pro", 180, "US$ 9,99", true),
            new Plan("Taller", 450, "US$ 19,99", false));

    private PricingDialog() {
    }

    /** @param perModel créditos que cuesta cada modelo según el backend */
    static void show(Window owner, int perModel) {
        Label title = new Label("Te quedaste sin créditos");
        title.getStyleClass().add("subtitle");
        Label intro = new Label("Cada modelo 3D usa " + perModel + " créditos. Elige un plan para seguir creando.");
        intro.getStyleClass().add("muted");
        intro.setWrapText(true);

        ButtonType close = new ButtonType("Ahora no", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.NONE, null, close);

        HBox plans = new HBox(16);
        for (Plan plan : PLANS) {
            plans.getChildren().add(card(plan, perModel, alert));
        }

        VBox content = new VBox(16, title, intro, plans);
        content.setPadding(new Insets(8));
        alert.getDialogPane().setContent(content);
        Dialogs.prepare(alert, owner, "Planes de créditos");
        alert.showAndWait();
    }

    private static VBox card(Plan plan, int perModel, Alert alert) {
        Label name = new Label(plan.name());
        name.getStyleClass().add("plan-name");

        Label credits = new Label(Credits.header(plan.credits()), Credits.icon(18));
        credits.getStyleClass().add("plan-credits");
        Label models = new Label("= " + Credits.models(plan.credits(), perModel) +" modelos 3D");
        models.getStyleClass().add("muted");
        Label price = new Label(plan.price());
        price.getStyleClass().add("plan-price");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Button choose = new Button("Elegir");
        choose.setMaxWidth(Double.MAX_VALUE);
        if (plan.featured()) {
            choose.getStyleClass().add("accent");
        }
        choose.setOnAction(e -> {
            alert.close();
            Dialogs.info(alert.getOwner(), "Plan " + plan.name(),
                    "Los pagos aún no están disponibles en esta versión. ¡Pronto podrás recargar créditos!");
        });

        VBox card = new VBox(8, name, credits, models, price, spacer, choose);
        card.getStyleClass().add("plan-card");
        if (plan.featured()) {
            card.getStyleClass().add("plan-featured");
            Label badge = new Label("Más popular");
            badge.getStyleClass().add("plan-badge");
            card.getChildren().add(0, badge);
        }
        card.setAlignment(Pos.TOP_LEFT);
        card.setPrefWidth(180);
        card.setMinHeight(220);
        return card;
    }
}
