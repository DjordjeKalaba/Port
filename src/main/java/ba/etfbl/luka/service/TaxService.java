package ba.etfbl.luka.service;

import ba.etfbl.luka.model.Vessel;

import java.time.Duration;
import java.time.LocalDateTime;

public final class TaxService {
    private TaxService() {
    }

    public static long calculateTax(Vessel vessel, LocalDateTime exitedAt) {
        if (vessel.isGovernmentVessel()) {
            return 0;
        }

        long hours = Math.max(1, Duration.between(vessel.getEnteredAt(), exitedAt).toHours());
        if (hours <= 12) {
            return 1000;
        }
        if (hours <= 24) {
            return 2000;
        }
        return 2000 + (hours - 24) * 100;
    }
}
