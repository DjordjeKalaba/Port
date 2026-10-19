package ba.etfbl.luka.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class Terminal implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final int dockCapacity;
    private final List<Vessel> vessels;

    public Terminal(int id, int dockCapacity) {
        this.id = id;
        this.dockCapacity = dockCapacity;
        this.vessels = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public int getDockCapacity() {
        return dockCapacity;
    }

    public List<Vessel> getVessels() {
        return vessels;
    }

    public int getFreeDockCount() {
        return Math.max(0, dockCapacity - occupiedDockIndexes().size());
    }

    public boolean hasFreeDock() {
        return getFreeDockCount() > 0;
    }

    public void addVessel(Vessel vessel) {
        addVessel(vessel, randomFreeDockIndex()
                .orElseThrow(() -> new IllegalStateException("Terminal " + id + " nema slobodnih vezova.")));
    }

    public void addVessel(Vessel vessel, int dockIndex) {
        if (dockIndex < 0 || dockIndex >= dockCapacity) {
            throw new IllegalArgumentException("Neispravan indeks veza.");
        }
        if (isDockOccupied(dockIndex)) {
            throw new IllegalStateException("Vez " + (dockIndex + 1) + " na terminalu " + id + " je zauzet.");
        }
        vessel.setTerminalId(id);
        vessel.setDockIndex(dockIndex);
        vessels.add(vessel);
        sortVessels();
    }

    public void removeVessel(Vessel vessel) {
        vessels.remove(vessel);
    }

    public Optional<Vessel> findById(String id) {
        return vessels.stream()
                .filter(vessel -> vessel.getId().equals(id))
                .findFirst();
    }

    public Optional<Integer> firstFreeDockIndex() {
        Set<Integer> occupied = occupiedDockIndexes();
        for (int i = 0; i < dockCapacity; i++) {
            if (!occupied.contains(i)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public Optional<Integer> randomFreeDockIndex() {
        List<Integer> freeIndexes = new ArrayList<>();
        Set<Integer> occupied = occupiedDockIndexes();
        for (int i = 0; i < dockCapacity; i++) {
            if (!occupied.contains(i)) {
                freeIndexes.add(i);
            }
        }
        if (freeIndexes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(freeIndexes.get(ThreadLocalRandom.current().nextInt(freeIndexes.size())));
    }

    public void randomizeDockIndexes() {
        List<Integer> dockIndexes = new ArrayList<>();
        for (int i = 0; i < dockCapacity; i++) {
            dockIndexes.add(i);
        }
        java.util.Collections.shuffle(dockIndexes);
        for (int i = 0; i < vessels.size(); i++) {
            vessels.get(i).setDockIndex(dockIndexes.get(i));
        }
        sortVessels();
    }

    public Optional<Vessel> findByDockIndex(int dockIndex) {
        return vessels.stream()
                .filter(vessel -> vessel.getDockIndex() == dockIndex)
                .findFirst();
    }

    public boolean isDockOccupied(int dockIndex) {
        return vessels.stream().anyMatch(vessel -> vessel.getDockIndex() == dockIndex);
    }

    public void normalizeDockIndexes() {
        Set<Integer> occupied = new java.util.HashSet<>();
        for (Vessel vessel : vessels) {
            int dockIndex = vessel.getDockIndex();
            if (dockIndex >= 0 && dockIndex < dockCapacity && !occupied.contains(dockIndex)) {
                occupied.add(dockIndex);
                continue;
            }
            firstFreeIndex(occupied).ifPresent(index -> {
                vessel.setDockIndex(index);
                occupied.add(index);
            });
        }
        sortVessels();
    }

    private Optional<Integer> firstFreeIndex(Set<Integer> occupied) {
        for (int i = 0; i < dockCapacity; i++) {
            if (!occupied.contains(i)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private Set<Integer> occupiedDockIndexes() {
        return vessels.stream()
                .map(Vessel::getDockIndex)
                .filter(index -> index >= 0 && index < dockCapacity)
                .collect(Collectors.toSet());
    }

    private void sortVessels() {
        vessels.sort(Comparator.comparingInt(Vessel::getDockIndex)
                .thenComparing(Vessel::getEnteredAt));
    }

    @Override
    public String toString() {
        return "Terminal " + id + " (" + getFreeDockCount() + " slobodno)";
    }
}
