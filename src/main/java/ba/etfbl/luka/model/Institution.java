package ba.etfbl.luka.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public enum Institution implements Serializable {
    COMMERCIAL("Komercijalno", 0, ""),
    CUSTOMS("Carina", 1, "C"),
    COAST_GUARD("Obalska straza", 2, "OS"),
    FIRE_DEPARTMENT("Vatrogasci", 3, "V");

    private final String label;
    private final int priority;
    private final String code;

    Institution(String label, int priority, String code) {
        this.label = label;
        this.priority = priority;
        this.code = code;
    }

    public int getPriority() {
        return priority;
    }

    public String getCode() {
        return code;
    }

    public boolean isGovernment() {
        return this != COMMERCIAL;
    }

    public static List<Institution> supportedFor(VesselType type) {
        return switch (type) {
            case CONTAINER_SHIP -> List.of(COMMERCIAL, COAST_GUARD);
            case CRUISE_SHIP -> List.of(COMMERCIAL, COAST_GUARD, CUSTOMS);
            case TANKER -> Arrays.asList(values());
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
