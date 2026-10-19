package ba.etfbl.luka.model;

import java.io.Serializable;

public enum VesselType implements Serializable {
    CONTAINER_SHIP("Kontejnerski brod", "Kapacitet TEU"),
    CRUISE_SHIP("Putnicki kruzer", "Broj putnika"),
    TANKER("Tanker", "Zapremina u barelima");

    private final String label;
    private final String extraFieldLabel;

    VesselType(String label, String extraFieldLabel) {
        this.label = label;
        this.extraFieldLabel = extraFieldLabel;
    }

    public String getLabel() {
        return label;
    }

    public String getExtraFieldLabel() {
        return extraFieldLabel;
    }

    @Override
    public String toString() {
        return label;
    }
}
