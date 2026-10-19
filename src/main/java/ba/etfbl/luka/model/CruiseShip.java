package ba.etfbl.luka.model;

public class CruiseShip extends Vessel {
    private static final long serialVersionUID = 1L;

    public CruiseShip() {
        super(VesselType.CRUISE_SHIP);
    }

    public int getPassengerCount() {
        return getSpecificValue();
    }

    public void setPassengerCount(int passengerCount) {
        setSpecificValue(passengerCount);
    }
}
