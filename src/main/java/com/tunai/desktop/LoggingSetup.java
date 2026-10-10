package com.tunai.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Envía el log a {@code %APPDATA%\TunAI\logs\tunai.log} (rotación de 3 archivos de 2 MB). */
final class LoggingSetup {

    private static FileHandler handler;

    private LoggingSetup() {
    }

    static synchronized void configure(Path logsDir) {
        shutdown();
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$-7s [%3$s] %5$s%6$s%n");
        try {
            Files.createDirectories(logsDir);
            handler = new FileHandler(logsDir.resolve("tunai%g.log").toString(), 2_000_000, 3, true);
            handler.setEncoding("UTF-8");
            handler.setFormatter(new SimpleFormatter());
            Logger root = Logger.getLogger("");
            root.addHandler(handler);
            root.setLevel(Level.INFO);
        } catch (IOException e) {
            Logger.getLogger(LoggingSetup.class.getName()).log(Level.WARNING, "No se pudo abrir el archivo de log", e);
        }
    }

    static synchronized void shutdown() {
        if (handler != null) {
            Logger.getLogger("").removeHandler(handler);
            handler.close();
            handler = null;
        }
    }
}
