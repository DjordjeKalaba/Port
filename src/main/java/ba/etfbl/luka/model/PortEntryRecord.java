package ba.etfbl.luka.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public record PortEntryRecord(
        String imoNumber,
        String vesselName,
        int terminalId,
        LocalDateTime enteredAt
) implements Serializable {
}
