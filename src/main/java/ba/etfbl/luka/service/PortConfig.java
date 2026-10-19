package ba.etfbl.luka.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public record PortConfig(int terminalCount, int dockCapacity, List<String> warnings) {
    private static final int DEFAULT_TERMINAL_COUNT = 3;
    private static final int DEFAULT_DOCK_CAPACITY = 28;

    public static PortConfig load() {
        Properties properties = new Properties();
        List<String> warnings = new ArrayList<>();
        Path externalConfig = Path.of("luka.properties");

        try {
            if (Files.exists(externalConfig)) {
                try (InputStream input = Files.newInputStream(externalConfig)) {
                    properties.load(input);
                }
            } else {
                try (InputStream input = PortConfig.class.getResourceAsStream("/ba/etfbl/luka/luka.properties")) {
                    if (input != null) {
                        properties.load(input);
                    } else {
                        warnings.add("Konfiguracioni fajl nije pronadjen. Koriste se podrazumijevane vrijednosti.");
                    }
                }
            }
        } catch (IOException exception) {
            AppLogger.error("Neuspjesno citanje konfiguracije luke.", exception);
            warnings.add("Konfiguracija nije mogla biti procitana. Koriste se podrazumijevane vrijednosti.");
        }

        int terminalCount = readInt(properties, "terminal.count", DEFAULT_TERMINAL_COUNT, warnings);
        int dockCapacity = readInt(properties, "terminal.dock.capacity", DEFAULT_DOCK_CAPACITY, warnings);
        return new PortConfig(Math.max(1, terminalCount), Math.max(1, dockCapacity), List.copyOf(warnings));
    }

    private static int readInt(Properties properties, String key, int defaultValue, List<String> warnings) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            warnings.add("Nedostaje " + key + ". Koristi se vrijednost " + defaultValue + ".");
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                warnings.add(key + " mora biti pozitivan broj. Koristi se vrijednost " + defaultValue + ".");
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            AppLogger.error("Neispravna numericna vrijednost za " + key + ".", exception);
            warnings.add("Neispravna vrijednost za " + key + ". Koristi se vrijednost " + defaultValue + ".");
            return defaultValue;
        }
    }
}
