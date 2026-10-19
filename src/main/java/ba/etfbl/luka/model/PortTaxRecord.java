package ba.etfbl.luka.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public record PortTaxRecord(
        String imoNumber,
        String vesselName,
        LocalDateTime enteredAt,
        LocalDateTime exitedAt,
        long chargedKm
) implements Serializable {
}
