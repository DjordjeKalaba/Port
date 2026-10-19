package ba.etfbl.luka.model;

public class Tanker extends Vessel {
    private static final long serialVersionUID = 1L;

    public Tanker() {
        super(VesselType.TANKER);
    }

    public int getBarrelVolume() {
        return getSpecificValue();
    }

    public void setBarrelVolume(int barrelVolume) {
        setSpecificValue(barrelVolume);
    }
}
