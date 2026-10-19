package ba.etfbl.luka.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Vessel implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private VesselType type;
    private Institution institution;
    private String name;
    private String imoNumber;
    private int engineCount;
    private String photoPath;
    private String registrationNumber;
    private int speedMillis;
    private boolean rotationOn;
    private int specificValue;
    private String wantedImoFilePath;
    private int terminalId;
    private int dockIndex = -1;
    private LocalDateTime enteredAt;

    public Vessel(VesselType type) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.institution = Institution.COMMERCIAL;
        this.speedMillis = ThreadLocalRandom.current().nextInt(550, 3401);
        this.enteredAt = LocalDateTime.now();
    }

    public static Vessel create(VesselType type) {
        return switch (type) {
            case CONTAINER_SHIP -> new ContainerShip();
            case CRUISE_SHIP -> new CruiseShip();
            case TANKER -> new Tanker();
        };
    }

    public String getId() {
        return id;
    }

    public VesselType getType() {
        return type;
    }

    public void setType(VesselType type) {
        this.type = type;
    }

    public Institution getInstitution() {
        return institution;
    }

    public void setInstitution(Institution institution) {
        this.institution = institution == null ? Institution.COMMERCIAL : institution;
        if (!this.institution.isGovernment()) {
            rotationOn = false;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImoNumber() {
        return imoNumber;
    }

    public void setImoNumber(String imoNumber) {
        this.imoNumber = imoNumber;
    }

    public int getEngineCount() {
        return engineCount;
    }

    public void setEngineCount(int engineCount) {
        this.engineCount = engineCount;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public int getSpeedMillis() {
        return speedMillis;
    }

    public void setSpeedMillis(int speedMillis) {
        this.speedMillis = speedMillis;
    }

    public boolean isRotationOn() {
        return rotationOn;
    }

    public void setRotationOn(boolean rotationOn) {
        this.rotationOn = rotationOn && institution != null && institution.isGovernment();
    }

    public int getSpecificValue() {
        return specificValue;
    }

    public void setSpecificValue(int specificValue) {
        this.specificValue = specificValue;
    }

    public String getWantedImoFilePath() {
        return wantedImoFilePath;
    }

    public void setWantedImoFilePath(String wantedImoFilePath) {
        this.wantedImoFilePath = wantedImoFilePath;
    }

    public int getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(int terminalId) {
        this.terminalId = terminalId;
    }

    public int getDockIndex() {
        return dockIndex;
    }

    public void setDockIndex(int dockIndex) {
        this.dockIndex = dockIndex;
    }

    public LocalDateTime getEnteredAt() {
        return enteredAt;
    }

    public void setEnteredAt(LocalDateTime enteredAt) {
        this.enteredAt = enteredAt;
    }

    public String getDisplayCode() {
        String typeCode = switch (type) {
            case CONTAINER_SHIP -> "K";
            case CRUISE_SHIP -> "P";
            case TANKER -> "T";
        };
        String institutionCode = institution == null || !institution.isGovernment() ? "" : institution.getCode();
        return typeCode + institutionCode + (rotationOn ? "R" : "");
    }

    public boolean isGovernmentVessel() {
        return institution != null && institution.isGovernment();
    }

    public Vessel copy() {
        Vessel copy = create(type);
        copy.setInstitution(institution);
        copy.setName(name);
        copy.setImoNumber(imoNumber);
        copy.setEngineCount(engineCount);
        copy.setPhotoPath(photoPath);
        copy.setRegistrationNumber(registrationNumber);
        copy.setSpeedMillis(speedMillis);
        copy.setRotationOn(rotationOn);
        copy.setSpecificValue(specificValue);
        copy.setWantedImoFilePath(wantedImoFilePath);
        copy.setTerminalId(terminalId);
        copy.setDockIndex(dockIndex);
        copy.setEnteredAt(enteredAt);
        return copy;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Vessel vessel)) {
            return false;
        }
        return Objects.equals(id, vessel.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
