package ba.etfbl.luka.simulation;

import ba.etfbl.luka.model.Terminal;
import ba.etfbl.luka.model.Vessel;
import ba.etfbl.luka.service.AppLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VesselThread extends Thread {
    private final SimulationService simulation;
    private final Vessel vessel;
    private final Object movementLock = new Object();
    private VesselMovement movement;
    private volatile boolean running = true;

    public VesselThread(SimulationService simulation, Vessel vessel) {
        super("Brod-" + threadNamePart(vessel));
        this.simulation = simulation;
        this.vessel = vessel;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            runMovementLoop();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            AppLogger.error("Greska u niti broda " + vessel.getName() + ".", exception);
        } finally {
            simulation.unregisterVesselThread(this);
        }
    }

    public void stopMovement() {
        running = false;
        synchronized (movementLock) {
            movementLock.notifyAll();
        }
        interrupt();
    }

    public void assignMovement(VesselMovement movement) {
        synchronized (movementLock) {
            this.movement = movement;
            movementLock.notifyAll();
        }
    }

    public boolean controls(Vessel candidate) {
        return vessel.equals(candidate);
    }

    public String getVesselId() {
        return vessel.getId();
    }

    private void runMovementLoop() throws InterruptedException {
        while (running) {
            VesselMovement activeMovement = waitForMovement();
            while (running && activeMovement != null) {
                waitForNextMove();
                if (running) {
                    activeMovement = performMovementStep(activeMovement);
                }
            }
        }
    }

    private VesselMovement waitForMovement() throws InterruptedException {
        synchronized (movementLock) {
            while (running && movement == null) {
                movementLock.wait();
            }
            return movement;
        }
    }

    private void waitForNextMove() throws InterruptedException {
        Thread.sleep(vessel.getSpeedMillis());
    }

    private VesselMovement performMovementStep(VesselMovement activeMovement) {
        synchronized (simulation) {
            if (!simulation.canMove(activeMovement)) {
                clearMovement(activeMovement);
                return null;
            }
            if (simulation.isPaused()) {
                return activeMovement;
            }

            long now = System.currentTimeMillis();
            simulation.performMaintenance(now);

            Optional<Terminal> terminalOptional = simulation.terminalOf(activeMovement);
            if (terminalOptional.isEmpty()) {
                clearMovement(activeMovement);
                return null;
            }

            Terminal terminal = terminalOptional.get();
            List<String> events = new ArrayList<>();
            boolean terminalBlocked = simulation.isBlocked(terminal, now);
            if (terminalBlocked && activeMovement.getDirection() != MovementDirection.RESPONDING) {
                return activeMovement;
            }
            if (simulation.continueOvertaking(terminal, activeMovement, now, events)) {
                simulation.addEvents(events);
                return activeMovement;
            }
            if (activeMovement.getDirection() != MovementDirection.RESPONDING
                    && simulation.shouldYieldToPriorityVessel(terminal, activeMovement)) {
                return activeMovement;
            }
            if (simulation.shouldWaitForWantedEscort(terminal, activeMovement)) {
                return activeMovement;
            }

            GridPosition next = activeMovement.nextPosition(TerminalLayout.rowsFor(terminal));
            if (next == null) {
                if (activeMovement.getDirection() == MovementDirection.RESPONDING && terminalBlocked) {
                    return activeMovement;
                }
                if (simulation.finishMovement(terminal, activeMovement, events)) {
                    simulation.removeMovement(activeMovement);
                    simulation.addEvents(events);
                    clearMovement(activeMovement);
                    return null;
                }
                simulation.addEvents(events);
                return activeMovement;
            }

            if (simulation.isCellAvailable(terminal, activeMovement, next)) {
                activeMovement.moveTo(next, now);
            } else {
                simulation.tryOvertaking(terminal, activeMovement, now, events);
            }
            simulation.addEvents(events);
            return activeMovement;
        }
    }

    private void clearMovement(VesselMovement finishedMovement) {
        synchronized (movementLock) {
            if (movement == finishedMovement) {
                movement = null;
            }
        }
    }

    private static String threadNamePart(Vessel vessel) {
        if (vessel.getImoNumber() != null && !vessel.getImoNumber().isBlank()) {
            return vessel.getImoNumber();
        }
        return vessel.getId();
    }
}
