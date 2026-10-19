package ba.etfbl.luka.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record InvestigationRecord(
        String type,
        int terminalId,
        LocalDateTime happenedAt,
        List<String> vesselImoNumbers,
        List<String> photoPaths,
        List<InvestigationParticipant> participants
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
