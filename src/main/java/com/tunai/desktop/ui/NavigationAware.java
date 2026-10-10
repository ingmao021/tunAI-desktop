package com.tunai.desktop.ui;

/** Implementado por los controladores que necesitan reaccionar al salir de su pantalla. */
public interface NavigationAware {

    /** Devuelve {@code false} para cancelar la navegación (por ejemplo, cambios sin guardar). */
    default boolean canLeave() {
        return true;
    }

    /** Libera recursos (temporizadores, animaciones, tareas) al salir de la pantalla. */
    default void onLeave() {
    }
}
