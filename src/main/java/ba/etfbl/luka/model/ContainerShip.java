package ba.etfbl.luka.model;

public class ContainerShip extends Vessel {
    private static final long serialVersionUID = 1L;

    public ContainerShip() {
        super(VesselType.CONTAINER_SHIP);
    }

    public int getTeuCapacity() {
        return getSpecificValue();
    }

    public void setTeuCapacity(int teuCapacity) {
        setSpecificValue(teuCapacity);
    }
}
