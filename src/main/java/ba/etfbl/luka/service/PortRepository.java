package ba.etfbl.luka.service;

import ba.etfbl.luka.model.Port;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class PortRepository {
    private static final Path PORT_FILE = Path.of("luka.ser");

    public Port load(PortConfig config) {
        if (!Files.exists(PORT_FILE)) {
            return new Port(config.terminalCount(), config.dockCapacity());
        }

        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(PORT_FILE))) {
            Port port = (Port) input.readObject();
            port.ensureTerminalCount(config.terminalCount(), config.dockCapacity());
            return port;
        } catch (IOException | ClassNotFoundException exception) {
            AppLogger.error("Neuspjesna deserijalizacija fajla luka.ser.", exception);
            return new Port(config.terminalCount(), config.dockCapacity());
        }
    }

    public void save(Port port) {
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(PORT_FILE))) {
            output.writeObject(port);
        } catch (IOException exception) {
            AppLogger.error("Neuspjesna serijalizacija fajla luka.ser.", exception);
            throw new IllegalStateException("Ne mogu sacuvati stanje luke.", exception);
        }
    }
}
