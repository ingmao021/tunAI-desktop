package com.tunai.desktop.ui;

import com.tunai.desktop.AppContext;
import com.tunai.desktop.garage.GarageVehicle;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** Visor / Editor (§5.5). Provisional: se completa en la fase del visor 3D. */
public class ViewerController implements NavigationAware {

    private final AppContext context;

    @FXML
    private Label titleLabel;

    public ViewerController(AppContext context) {
        this.context = context;
    }

    public void open(GarageVehicle vehicle) {
        titleLabel.setText(vehicle.name());
    }

    @FXML
    private void onBack() {
        context.navigator().goTo(Navigator.Screen.GARAGE);
    }
}
