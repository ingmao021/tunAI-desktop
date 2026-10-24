package com.tunai.desktop.ui;

import com.tunai.desktop.api.Quota;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

/** Estado compartido entre pantallas. Solo se modifica en el hilo de JavaFX. */
public class SessionState {

    public enum ServerStatus { CONNECTING, READY, OFFLINE }

    private final ObjectProperty<ServerStatus> serverStatus = new SimpleObjectProperty<>(ServerStatus.CONNECTING);
    /** Última cuota conocida; nula hasta la primera respuesta del backend. */
    private final ObjectProperty<Quota> quota = new SimpleObjectProperty<>();
    private final IntegerProperty credits = new SimpleIntegerProperty();
    /** Lo que cuesta cada modelo según el backend; el valor por defecto hasta su primera respuesta. */
    private final IntegerProperty creditsPerModel = new SimpleIntegerProperty(Credits.DEFAULT_PER_MODEL);

    public ObjectProperty<ServerStatus> serverStatusProperty() {
        return serverStatus;
    }

    public ServerStatus getServerStatus() {
        return serverStatus.get();
    }

    public ObjectProperty<Quota> quotaProperty() {
        return quota;
    }

    public Quota getQuota() {
        return quota.get();
    }

    public IntegerProperty creditsProperty() {
        return credits;
    }

    public int getCredits() {
        return credits.get();
    }

    public IntegerProperty creditsPerModelProperty() {
        return creditsPerModel;
    }

    public int getCreditsPerModel() {
        return creditsPerModel.get();
    }

    /** Alcanzan los créditos para un modelo más. */
    public boolean canGenerate() {
        return Credits.canGenerate(getCredits(), getCreditsPerModel());
    }
}
