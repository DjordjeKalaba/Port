package ba.etfbl.luka.model;

import java.io.Serializable;

public record InvestigationParticipant(
        String name,
        String imoNumber,
        String registrationNumber,
        VesselType vesselType,
        Institution institution,
        String photoPath
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
