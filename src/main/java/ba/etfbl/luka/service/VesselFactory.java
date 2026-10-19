package ba.etfbl.luka.service;

import ba.etfbl.luka.model.Institution;
import ba.etfbl.luka.model.Vessel;
import ba.etfbl.luka.model.VesselType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class VesselFactory {
    private static final String[] NAMES = {
            "Vrbas", "Sana", "Una", "Jadran", "Kozara", "Romanija", "Trebinje", "Leotar"
    };

    private VesselFactory() {
    }

    public static Vessel randomVessel() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        VesselType type = VesselType.values()[random.nextInt(VesselType.values().length)];
        Vessel vessel = Vessel.create(type);
        vessel.setName(NAMES[random.nextInt(NAMES.length)] + "-" + random.nextInt(100, 1000));
        vessel.setImoNumber(String.valueOf(random.nextLong(1000000, 9999999)));
        vessel.setEngineCount(random.nextInt(1, 5));
        vessel.setRegistrationNumber("REG-" + random.nextInt(1000, 9999));
        vessel.setPhotoPath("");
        vessel.setSpecificValue(switch (type) {
            case CONTAINER_SHIP -> random.nextInt(500, 20000);
            case CRUISE_SHIP -> random.nextInt(50, 5000);
            case TANKER -> random.nextInt(1000, 500000);
        });
        vessel.setEnteredAt(LocalDateTime.now().minusHours(random.nextInt(1, 36)));

        if (random.nextInt(100) >= 90) {
            List<Institution> institutions = Institution.supportedFor(type).stream()
                    .filter(Institution::isGovernment)
                    .toList();
            if (!institutions.isEmpty()) {
                vessel.setInstitution(institutions.get(random.nextInt(institutions.size())));
                vessel.setRotationOn(random.nextBoolean());
            }
        }
        return vessel;
    }
}
