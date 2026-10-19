package ba.etfbl.luka.service;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class AppLogger {
    private static final Logger LOGGER = Logger.getLogger("ba.etfbl.luka");

    static {
        try {
            LOGGER.setUseParentHandlers(false);
            FileHandler handler = new FileHandler("error.log", true);
            handler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(handler);
        } catch (IOException exception) {
            LOGGER.log(Level.SEVERE, "Neuspjesno inicijalizovan error.log.", exception);
        }
    }

    private AppLogger() {
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
    }

    public static void info(String message) {
        LOGGER.info(message);
    }
}
