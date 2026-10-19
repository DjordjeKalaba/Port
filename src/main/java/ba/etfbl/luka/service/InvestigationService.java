package ba.etfbl.luka.service;

import ba.etfbl.luka.model.InvestigationRecord;
import ba.etfbl.luka.model.InvestigationParticipant;
import ba.etfbl.luka.model.Vessel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class InvestigationService {
    private static final String INVESTIGATION_FOLDER = "luka-uvidjaji";

    private InvestigationService() {
    }

    public static Optional<Path> save(String type, int terminalId, List<Vessel> vessels) {
        InvestigationRecord record = new InvestigationRecord(
                type,
                terminalId,
                LocalDateTime.now(),
                vessels.stream().map(Vessel::getImoNumber).toList(),
                vessels.stream().map(Vessel::getPhotoPath).toList(),
                vessels.stream().map(InvestigationService::participant).toList()
        );

        Path folder = investigationFolder();
        Path file = folder.resolve("uvidjaj-" + System.currentTimeMillis() + ".bin");
        try {
            Files.createDirectories(folder);
            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(file))) {
                output.writeObject(record);
            }
            return Optional.of(file);
        } catch (IOException exception) {
            AppLogger.error("Neuspjesno cuvanje uvidjaja.", exception);
            return Optional.empty();
        }
    }

    public static List<SavedInvestigation> listSaved() {
        Path folder = investigationFolder();
        if (!Files.isDirectory(folder)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(folder)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".bin"))
                    .map(InvestigationService::read)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing((SavedInvestigation item) -> item.record().happenedAt()).reversed())
                    .toList();
        } catch (IOException exception) {
            AppLogger.error("Neuspjesno citanje foldera uvidjaja.", exception);
            return List.of();
        }
    }

    private static Optional<SavedInvestigation> read(Path path) {
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(path))) {
            Object value = input.readObject();
            if (value instanceof InvestigationRecord record) {
                return Optional.of(new SavedInvestigation(path, record));
            }
        } catch (IOException | ClassNotFoundException exception) {
            AppLogger.error("Neuspjesno citanje uvidjaja: " + path, exception);
        }
        return Optional.empty();
    }

    private static Path investigationFolder() {
        return Path.of(System.getProperty("user.home"), INVESTIGATION_FOLDER);
    }

    private static InvestigationParticipant participant(Vessel vessel) {
        return new InvestigationParticipant(
                vessel.getName(),
                vessel.getImoNumber(),
                vessel.getRegistrationNumber(),
                vessel.getType(),
                vessel.getInstitution(),
                vessel.getPhotoPath()
        );
    }

    public record SavedInvestigation(Path path, InvestigationRecord record) {
    }
}
