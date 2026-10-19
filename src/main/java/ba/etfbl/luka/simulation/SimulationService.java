package ba.etfbl.luka.simulation;

import ba.etfbl.luka.model.Institution;
import ba.etfbl.luka.model.Port;
import ba.etfbl.luka.model.Terminal;
import ba.etfbl.luka.model.Vessel;
import ba.etfbl.luka.service.AppLogger;
import ba.etfbl.luka.service.InvestigationService;
import ba.etfbl.luka.service.TaxService;
import ba.etfbl.luka.service.VesselFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SimulationService {
    private final Port port;
    private final Map<Integer, List<VesselMovement>> movementsByTerminal = new HashMap<>();
    private final Map<Integer, Set<Integer>> reservedDockIndexes = new HashMap<>();
    private final Map<Integer, Long> blockedUntil = new HashMap<>();
    private final Map<Integer, List<VesselMovement>> accidentParticipantsByTerminal = new HashMap<>();
    private final Map<Integer, List<VesselMovement>> accidentRespondersByTerminal = new HashMap<>();
    private final Map<Integer, Integer> accidentDurationsByTerminal = new HashMap<>();
    private final Map<String, String> wantedEscortLeaders = new HashMap<>();
    private final Map<String, String> wantedEscortFollowers = new HashMap<>();
    private final Set<String> handledAccidentEncounters = new HashSet<>();
    private final Set<String> handledWantedVessels = new HashSet<>();
    private final Set<String> vesselsInWantedInvestigation = new HashSet<>();
    private final List<PendingWantedEscort> pendingWantedEscorts = new ArrayList<>();
    private final Map<String, VesselThread> vesselThreads = new HashMap<>();
    private final Queue<String> eventQueue = new ConcurrentLinkedQueue<>();
    private boolean running;
    private boolean paused;

    public SimulationService(Port port) {
        this.port = port;
    }

    public synchronized void startThreadedSimulation(int minimum) {
        stopMovementThreads();
        eventQueue.clear();
        ensureMinimumVessels(minimum);
        resetSimulationState();
        running = true;
        paused = false;
        List<String> startupEvents = new ArrayList<>();
        detectWantedVessels(startupEvents);
        addEvents(startupEvents);
        selectLeavingVessels();
        startVesselThreadsForAllVessels();
        addEvent("Simulacija je pokrenuta. Svaki brod radi u sopstvenoj niti.");
    }

    public synchronized void shutdown() {
        stopMovementThreads();
    }

    public synchronized void setPaused(boolean paused) {
        this.paused = paused;
    }

    public synchronized List<String> drainEvents() {
        List<String> events = new ArrayList<>();
        String event;
        while ((event = eventQueue.poll()) != null) {
            events.add(event);
        }
        return events;
    }

    public synchronized boolean isComplete() {
        long now = System.currentTimeMillis();
        performMaintenance(now);
        return movementsByTerminal.values().stream().allMatch(List::isEmpty)
                && pendingWantedEscorts.isEmpty()
                && blockedUntil.values().stream().noneMatch(blockedUntilMillis -> blockedUntilMillis > now);
    }

    public synchronized void tick() {
        performMaintenance(System.currentTimeMillis());
    }

    public synchronized void ensureMinimumVessels(int minimum) {
        for (Terminal terminal : port.getTerminals()) {
            while (terminal.getVessels().size() < minimum && terminal.hasFreeDock()) {
                Vessel vessel = VesselFactory.randomVessel();
                terminal.addVessel(vessel, terminal.randomFreeDockIndex().orElseThrow());
                port.recordEntry(vessel);
            }
        }
    }

    public synchronized void randomizeDockedVessels() {
        port.getTerminals().forEach(Terminal::randomizeDockIndexes);
    }

    public synchronized void selectLeavingVessels() {
        for (Terminal terminal : port.getTerminals()) {
            List<Vessel> shuffled = terminal.getVessels().stream()
                    .filter(vessel -> !isInPendingWantedEscort(vessel))
                    .filter(vessel -> !isInWantedInvestigation(vessel))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            Collections.shuffle(shuffled);
            int count = (int) Math.ceil(shuffled.size() * 0.15);
            for (Vessel vessel : shuffled.subList(0, Math.min(count, shuffled.size()))) {
                terminal.removeVessel(vessel);
                addMovement(terminal, VesselMovement.leaving(vessel, TerminalLayout.rowsFor(terminal)));
            }
        }
    }

    private void resetSimulationState() {
        movementsByTerminal.clear();
        reservedDockIndexes.clear();
        blockedUntil.clear();
        accidentParticipantsByTerminal.clear();
        accidentRespondersByTerminal.clear();
        accidentDurationsByTerminal.clear();
        wantedEscortLeaders.clear();
        wantedEscortFollowers.clear();
        handledAccidentEncounters.clear();
        handledWantedVessels.clear();
        vesselsInWantedInvestigation.clear();
        pendingWantedEscorts.clear();
    }

    public synchronized Optional<DockReservation> findFreeDockForNewVessel() {
        return findFreeDockForNewVessel(null);
    }

    public synchronized Optional<EntryReservation> findEntryReservation() {
        List<Integer> skippedTerminalIds = new ArrayList<>();
        for (Terminal terminal : port.getTerminals()) {
            Optional<DockReservation> reservation = findFreeDock(terminal);
            if (reservation.isPresent()) {
                return Optional.of(new EntryReservation(reservation.get(), List.copyOf(skippedTerminalIds)));
            }
            skippedTerminalIds.add(terminal.getId());
        }
        return Optional.empty();
    }

    public synchronized Optional<DockReservation> findFreeDockForNewVessel(Terminal preferredTerminal) {
        if (preferredTerminal != null) {
            Optional<DockReservation> preferredReservation = findFreeDock(preferredTerminal);
            if (preferredReservation.isPresent()) {
                return preferredReservation;
            }
        }

        for (Terminal terminal : port.getTerminals()) {
            if (preferredTerminal != null && terminal.getId() == preferredTerminal.getId()) {
                continue;
            }
            Optional<DockReservation> reservation = findFreeDock(terminal);
            if (reservation.isPresent()) {
                return reservation;
            }
        }
        return Optional.empty();
    }

    private Optional<DockReservation> findFreeDock(Terminal terminal) {
        List<Integer> freeDockIndexes = new ArrayList<>();
        Set<Integer> reserved = reservedDockIndexes.getOrDefault(terminal.getId(), Set.of());
        for (int dockIndex = 0; dockIndex < terminal.getDockCapacity(); dockIndex++) {
            if (!terminal.isDockOccupied(dockIndex) && !reserved.contains(dockIndex)) {
                freeDockIndexes.add(dockIndex);
            }
        }
        if (freeDockIndexes.isEmpty()) {
            return Optional.empty();
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(freeDockIndexes.size());
        return Optional.of(new DockReservation(terminal, freeDockIndexes.get(randomIndex)));
    }

    public synchronized String startEntering(DockReservation reservation, Vessel vessel) {
        vessel.setTerminalId(reservation.terminal().getId());
        vessel.setDockIndex(reservation.dockIndex());
        port.recordEntry(vessel);
        addMovement(reservation.terminal(), VesselMovement.entering(vessel, reservation.dockIndex()));
        reservedDockIndexes.computeIfAbsent(reservation.terminal().getId(), key -> new HashSet<>()).add(reservation.dockIndex());
        return "Novo plovilo " + vessel.getName() + " ulazi prema terminalu " + reservation.terminal().getId() + ".";
    }

    public synchronized String startEntering(EntryReservation entryReservation, Vessel vessel) {
        DockReservation reservation = entryReservation.dockReservation();
        String skipped = entryReservation.skippedTerminalIds().isEmpty()
                ? ""
                : " Proslo je pravo kroz popunjene terminale: " + entryReservation.skippedTerminalIds() + ".";
        return startEntering(reservation, vessel) + skipped;
    }

    public synchronized Map<GridPosition, String> movingLabels(Terminal terminal) {
        Map<GridPosition, String> labels = new HashMap<>();
        for (VesselMovement movement : movementsFor(terminal)) {
            labels.put(movement.getPosition(), movement.getVessel().getDisplayCode());
        }
        return labels;
    }

    public synchronized Map<GridPosition, Vessel> movingVessels(Terminal terminal) {
        Map<GridPosition, Vessel> vessels = new HashMap<>();
        for (VesselMovement movement : movementsFor(terminal)) {
            vessels.put(movement.getPosition(), movement.getVessel());
        }
        return vessels;
    }

    public synchronized Map<GridPosition, MovementDirection> movingDirections(Terminal terminal) {
        Map<GridPosition, MovementDirection> directions = new HashMap<>();
        for (VesselMovement movement : movementsFor(terminal)) {
            directions.put(movement.getPosition(), movement.getDirection());
        }
        return directions;
    }

    public synchronized boolean isBlocked(Terminal terminal) {
        return blockedUntil.getOrDefault(terminal.getId(), 0L) > System.currentTimeMillis();
    }

    public synchronized Optional<Vessel> dockVessel(Terminal terminal, int dockIndex) {
        if (dockIndex >= terminal.getDockCapacity()) {
            return Optional.empty();
        }
        return terminal.findByDockIndex(dockIndex);
    }

    public synchronized int freeDockCount(Terminal terminal) {
        return terminal.getFreeDockCount();
    }

    public synchronized int totalFreeDocks() {
        return port.getTerminals().stream()
                .mapToInt(Terminal::getFreeDockCount)
                .sum();
    }

    public synchronized boolean isDuplicateImo(String imoNumber) {
        boolean inDockedVessels = port.getTerminals().stream()
                .flatMap(terminal -> terminal.getVessels().stream())
                .anyMatch(vessel -> vessel.getImoNumber() != null && vessel.getImoNumber().equalsIgnoreCase(imoNumber));
        boolean inMovingVessels = movementsByTerminal.values().stream()
                .flatMap(List::stream)
                .map(VesselMovement::getVessel)
                .anyMatch(vessel -> vessel.getImoNumber() != null && vessel.getImoNumber().equalsIgnoreCase(imoNumber));
        return inDockedVessels || inMovingVessels;
    }

    boolean canMove(VesselMovement movement) {
        return running && movementsByTerminal.values().stream()
                .anyMatch(movements -> movements.contains(movement));
    }

    boolean isPaused() {
        return paused;
    }

    boolean isBlocked(Terminal terminal, long now) {
        return blockedUntil.getOrDefault(terminal.getId(), 0L) > now;
    }

    void performMaintenance(long now) {
        List<String> events = new ArrayList<>();
        processPendingWantedEscorts(now, events);
        detectWantedVessels(events);

        for (Terminal terminal : port.getTerminals()) {
            List<VesselMovement> movements = movementsFor(terminal);
            startAccidentTimerIfRespondersArrived(terminal, now, events);
            boolean accidentJustCleared = clearExpiredAccidentIfNeeded(terminal, movements, now, events);
            if (blockedUntil.getOrDefault(terminal.getId(), 0L) > now) {
                continue;
            }
            if (!accidentJustCleared) {
                maybeHandleAccident(terminal, movements, events);
            }
        }
        events.forEach(this::addEvent);
    }

    private void startAccidentTimerIfRespondersArrived(Terminal terminal, long now, List<String> events) {
        if (blockedUntil.getOrDefault(terminal.getId(), 0L) != Long.MAX_VALUE) {
            return;
        }

        List<VesselMovement> responders = accidentRespondersByTerminal.getOrDefault(terminal.getId(), List.of());
        boolean responderArrived = responders.isEmpty() || responders.stream()
                .anyMatch(movement -> movement.getPosition().equals(movement.getTargetPosition()));
        if (!responderArrived) {
            return;
        }

        int seconds = accidentDurationsByTerminal.getOrDefault(terminal.getId(), 3);
        blockedUntil.put(terminal.getId(), now + seconds * 1000L);
        events.add("Sluzba je stigla na mjesto sudara na terminalu " + terminal.getId()
                + ". Uvidjaj traje " + seconds + " s.");
    }

    Optional<Terminal> terminalOf(VesselMovement movement) {
        for (Map.Entry<Integer, List<VesselMovement>> entry : movementsByTerminal.entrySet()) {
            if (entry.getValue().contains(movement)) {
                return port.findTerminal(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private boolean clearExpiredAccidentIfNeeded(
            Terminal terminal,
            List<VesselMovement> movements,
            long now,
            List<String> events
    ) {
        Long blockedUntilMillis = blockedUntil.get(terminal.getId());
        if (blockedUntilMillis == null || blockedUntilMillis > now) {
            return false;
        }

        List<VesselMovement> responders = movements.stream()
                .filter(movement -> movement.getDirection() == MovementDirection.RESPONDING)
                .toList();
        responders.forEach(movement -> returnResponderToDock(movement, events));
        movements.removeAll(responders);
        sendAccidentParticipantsToExit(terminal, events);
        blockedUntil.remove(terminal.getId());
        accidentRespondersByTerminal.remove(terminal.getId());
        accidentDurationsByTerminal.remove(terminal.getId());
        events.add("Uvidjaj na terminalu " + terminal.getId() + " je zavrsen. Saobracaj je nastavljen.");
        return true;
    }

    private void sendAccidentParticipantsToExit(Terminal terminal, List<String> events) {
        List<VesselMovement> participants = accidentParticipantsByTerminal.remove(terminal.getId());
        if (participants == null || participants.isEmpty()) {
            return;
        }

        List<VesselMovement> terminalMovements = movementsFor(terminal);
        for (VesselMovement participant : participants) {
            if (!terminalMovements.remove(participant)) {
                continue;
            }
            VesselMovement exitMovement = VesselMovement.leavingFromPosition(
                    participant.getVessel(),
                    participant.getPosition()
            );
            terminalMovements.add(exitMovement);
            assignMovementToVesselThread(exitMovement);
            events.add(participant.getVessel().getName()
                    + " nakon uvidjaja napusta terminal " + terminal.getId() + ".");
        }
    }

    boolean isCellAvailable(Terminal terminal, VesselMovement current, GridPosition position) {
        if (TerminalLayout.isDockRow(position.row())) {
            return true;
        }
        return movementsFor(terminal).stream()
                .filter(movement -> !movement.equals(current))
                .noneMatch(movement -> movement.getPosition().equals(position));
    }

    void tryOvertaking(Terminal terminal, VesselMovement movement, long now, List<String> events) {
        if (movement.isOvertaking()) {
            return;
        }

        int rows = TerminalLayout.rowsFor(terminal);
        GridPosition alternate = movement.overtakingPosition(rows);
        GridPosition returnPosition = movement.initialOvertakingReturnPosition(rows);
        if (movement.getDirection() == MovementDirection.RESPONDING) {
            GridPosition skipPosition = movement.skipBlockedPosition(rows);
            if (skipPosition != null && isCellAvailable(terminal, movement, skipPosition)) {
                movement.bypassTo(skipPosition, now);
                events.add(movement.getVessel().getDisplayCode()
                        + " pod rotacijom preskace blokirano polje na terminalu " + terminal.getId() + ".");
                return;
            }
        }
        if (movement.getDirection() == MovementDirection.RESPONDING
                && alternate != null
                && returnPosition != null
                && isChannelCellAvailable(terminal, movement, alternate)) {
            movement.startOvertaking(alternate, returnPosition, now);
            events.add(movement.getVessel().getDisplayCode()
                    + " pod rotacijom obilazi zaustavljeno plovilo na terminalu " + terminal.getId() + ".");
            return;
        }
        if (alternate != null
                && returnPosition != null
                && isChannelCellAvailable(terminal, movement, alternate)) {
            Vessel vessel = movement.getVessel();
            movement.startOvertaking(alternate, returnPosition, now);
            events.add(vessel.getDisplayCode() + " pretice na terminalu " + terminal.getId() + ".");
        }
    }

    boolean continueOvertaking(Terminal terminal, VesselMovement movement, long now, List<String> events) {
        if (!movement.isOvertaking()) {
            return false;
        }

        int rows = TerminalLayout.rowsFor(terminal);
        GridPosition returnPosition = movement.overtakingReturnPosition(rows);
        if (returnPosition != null && isChannelCellAvailable(terminal, movement, returnPosition)) {
            movement.moveTo(returnPosition, now);
            return true;
        }

        GridPosition forwardPosition = movement.overtakingForwardPosition(rows);
        if (forwardPosition != null && isChannelCellAvailable(terminal, movement, forwardPosition)) {
            movement.moveTo(forwardPosition, now);
            return true;
        }

        return true;
    }

    private boolean isChannelCellAvailable(Terminal terminal, VesselMovement current, GridPosition position) {
        if (!TerminalLayout.isChannelRow(position.row())) {
            return false;
        }
        return movementsFor(terminal).stream()
                .filter(movement -> !movement.equals(current))
                .noneMatch(movement -> movement.getPosition().equals(position));
    }

    boolean finishMovement(Terminal terminal, VesselMovement movement, List<String> events) {
        Vessel vessel = movement.getVessel();
        if (movement.getDirection() == MovementDirection.RESPONDING) {
            returnResponderToDock(movement, events);
            return true;
        }
        if (movement.getDirection() == MovementDirection.LEAVING) {
            Optional<Terminal> nextTerminal = port.findTerminal(terminal.getId() + 1);
            if (nextTerminal.isPresent()) {
                return continueLeavingThroughNextTerminal(terminal, nextTerminal.get(), movement, events);
            }
            events.add(registerExit(vessel, terminal.getId()));
            return true;
        }

        terminal.addVessel(vessel, movement.getTargetDockIndex());
        reservedDockIndexes.getOrDefault(terminal.getId(), Set.of()).remove(movement.getTargetDockIndex());
        events.add(vessel.getName() + " se privezao na terminal " + terminal.getId() + ".");
        return true;
    }

    private boolean continueLeavingThroughNextTerminal(
            Terminal currentTerminal,
            Terminal nextTerminal,
            VesselMovement movement,
            List<String> events
    ) {
        GridPosition entryPosition = new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, 0);
        if (!isChannelCellAvailable(nextTerminal, movement, entryPosition)) {
            return false;
        }

        VesselMovement nextMovement = VesselMovement.leavingFromPosition(
                movement.getVessel(),
                entryPosition
        );
        movement.getVessel().setTerminalId(nextTerminal.getId());
        addMovement(nextTerminal, nextMovement);
        events.add(movement.getVessel().getName()
                + " je izasao sa terminala " + currentTerminal.getId()
                + " i prolazi kroz terminal " + nextTerminal.getId() + ".");
        return true;
    }

    private boolean maybeHandleAccident(Terminal terminal, List<VesselMovement> movements, List<String> events) {
        List<VesselMovement> channelMovements = movements.stream()
                .filter(movement -> TerminalLayout.isChannelRow(movement.getPosition().row()))
                .toList();
        Optional<AccidentEncounter> encounter = findAccidentEncounter(channelMovements);
        if (encounter.isEmpty() || ThreadLocalRandom.current().nextInt(100) >= 50) {
            return false;
        }

        AccidentEncounter accident = encounter.get();
        handledAccidentEncounters.add(accidentKey(accident.first().getVessel(), accident.second().getVessel()));
        accidentParticipantsByTerminal.put(terminal.getId(), List.of(accident.first(), accident.second()));
        int seconds = ThreadLocalRandom.current().nextInt(3, 11);
        blockedUntil.put(terminal.getId(), Long.MAX_VALUE);
        accidentDurationsByTerminal.put(terminal.getId(), seconds);
        List<Vessel> involved = new ArrayList<>(List.of(
                accident.first().getVessel(),
                accident.second().getVessel()
        ));
        List<VesselMovement> responders = activateEmergencyResponders(terminal, accident);
        accidentRespondersByTerminal.put(terminal.getId(), responders);
        involved.addAll(responders.stream().map(VesselMovement::getVessel).toList());

        String fileInfo = InvestigationService.save("SUDAR", terminal.getId(), involved)
                .map(path -> " Uvidjaj: " + path)
                .orElse("");
        String respondersText = involved.size() > 2
                ? " Poslate sluzbe: " + involved.stream()
                .skip(2)
                .map(vessel -> vessel.getInstitution() + " " + vessel.getName())
                .toList() + "."
                : "";
        events.add("Sudar na terminalu " + terminal.getId()
                + ". Saobracaj je blokiran. Uvidjaj ce trajati " + seconds + " s nakon dolaska sluzbi."
                + respondersText + fileInfo);
        return true;
    }

    private Optional<AccidentEncounter> findAccidentEncounter(List<VesselMovement> channelMovements) {
        for (int i = 0; i < channelMovements.size(); i++) {
            VesselMovement first = channelMovements.get(i);
            for (int j = i + 1; j < channelMovements.size(); j++) {
                VesselMovement second = channelMovements.get(j);
                if (!canCauseAccident(first, second)) {
                    continue;
                }
                if (handledAccidentEncounters.contains(accidentKey(first.getVessel(), second.getVessel()))) {
                    continue;
                }
                return Optional.of(new AccidentEncounter(first, second));
            }
        }
        return Optional.empty();
    }

    private boolean canCauseAccident(VesselMovement first, VesselMovement second) {
        if (first.getDirection() == MovementDirection.RESPONDING || second.getDirection() == MovementDirection.RESPONDING) {
            return false;
        }
        if (first.getDirection() == second.getDirection()) {
            return false;
        }
        if (first.getPosition().row() == second.getPosition().row()) {
            return false;
        }
        return Math.abs(first.getPosition().column() - second.getPosition().column()) <= 1;
    }

    private String accidentKey(Vessel first, Vessel second) {
        if (first.getId().compareTo(second.getId()) <= 0) {
            return first.getId() + ":" + second.getId();
        }
        return second.getId() + ":" + first.getId();
    }

    boolean shouldYieldToPriorityVessel(Terminal terminal, VesselMovement movement) {
        int ownPriority = activePriority(movement.getVessel());
        if (ownPriority >= Institution.FIRE_DEPARTMENT.getPriority()) {
            return false;
        }
        GridPosition position = movement.getPosition();
        return movementsFor(terminal).stream()
                .filter(other -> !other.equals(movement))
                .filter(other -> !isWantedEscortPair(movement.getVessel(), other.getVessel()))
                .filter(other -> activePriority(other.getVessel()) > ownPriority)
                .filter(other -> TerminalLayout.isChannelRow(other.getPosition().row()))
                .anyMatch(other -> Math.abs(other.getPosition().column() - position.column()) <= 1);
    }

    boolean shouldWaitForWantedEscort(Terminal terminal, VesselMovement movement) {
        if (movement.getDirection() != MovementDirection.LEAVING) {
            return false;
        }

        Vessel vessel = movement.getVessel();
        String followerId = wantedEscortFollowers.get(vessel.getId());
        if (followerId != null) {
            return findMovementByVesselId(terminal, followerId)
                    .filter(follower -> isTooFarAhead(terminal, movement, follower))
                    .isPresent();
        }

        String leaderId = wantedEscortLeaders.get(vessel.getId());
        if (leaderId != null) {
            return findMovementByVesselId(terminal, leaderId)
                    .filter(leader -> isAheadOfOrTooCloseToLeader(terminal, movement, leader))
                    .isPresent();
        }

        return false;
    }

    private Optional<VesselMovement> findMovementByVesselId(Terminal terminal, String vesselId) {
        return movementsFor(terminal).stream()
                .filter(movement -> movement.getVessel().getId().equals(vesselId))
                .findFirst();
    }

    private boolean isTooFarAhead(Terminal terminal, VesselMovement leader, VesselMovement follower) {
        int rows = TerminalLayout.rowsFor(terminal);
        return follower.remainingDistanceToExit(rows) - leader.remainingDistanceToExit(rows) > 2;
    }

    private boolean isAheadOfOrTooCloseToLeader(Terminal terminal, VesselMovement follower, VesselMovement leader) {
        int rows = TerminalLayout.rowsFor(terminal);
        return follower.remainingDistanceToExit(rows) <= leader.remainingDistanceToExit(rows) + 1;
    }

    private int activePriority(Vessel vessel) {
        if (vessel == null || !vessel.isGovernmentVessel() || !vessel.isRotationOn()) {
            return 0;
        }
        return vessel.getInstitution().getPriority();
    }

    private List<VesselMovement> activateEmergencyResponders(Terminal incidentTerminal, AccidentEncounter accident) {
        List<VesselMovement> responders = new ArrayList<>();
        Set<GridPosition> assignedTargets = new HashSet<>();
        for (Institution institution : List.of(Institution.FIRE_DEPARTMENT, Institution.COAST_GUARD, Institution.CUSTOMS)) {
            port.getTerminals().stream()
                    .flatMap(terminal -> terminal.getVessels().stream()
                            .filter(vessel -> vessel.getInstitution() == institution)
                            .filter(vessel -> !isInPendingWantedEscort(vessel))
                            .map(vessel -> new ResponderLocation(terminal, vessel)))
                    .min(Comparator.comparingInt(location -> Math.abs(location.terminal().getId() - incidentTerminal.getId())))
                    .ifPresent(location -> {
                        Optional<GridPosition> targetPosition = findResponderTargetPosition(incidentTerminal, accident, assignedTargets);
                        if (targetPosition.isEmpty()) {
                            return;
                        }
                        Vessel vessel = location.vessel();
                        int returnDockIndex = vessel.getDockIndex();
                        vessel.setRotationOn(true);
                        location.terminal().removeVessel(vessel);
                        reservedDockIndexes.computeIfAbsent(location.terminal().getId(), key -> new HashSet<>()).add(returnDockIndex);
                        assignedTargets.add(targetPosition.get());
                        GridPosition startPosition = location.terminal().getId() == incidentTerminal.getId()
                                ? TerminalLayout.dockPosition(returnDockIndex, TerminalLayout.rowsFor(incidentTerminal))
                                : new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, 0);
                        VesselMovement responderMovement = VesselMovement.responding(
                                vessel,
                                targetPosition.get(),
                                location.terminal().getId(),
                                returnDockIndex,
                                startPosition
                        );
                        addMovement(incidentTerminal, responderMovement);
                        responders.add(responderMovement);
                    });
        }
        return responders;
    }

    private Optional<GridPosition> findResponderTargetPosition(
            Terminal terminal,
            AccidentEncounter accident,
            Set<GridPosition> assignedTargets
    ) {
        int rows = TerminalLayout.rowsFor(terminal);
        int maxDistance = rows + 2;
        for (int distance = 1; distance <= maxDistance; distance++) {
            for (int column = 0; column < rows; column++) {
                for (int row : List.of(TerminalLayout.INBOUND_CHANNEL_ROW, TerminalLayout.OUTBOUND_CHANNEL_ROW)) {
                    GridPosition position = new GridPosition(row, column);
                    if (distanceFromAccident(position, accident) == distance
                            && !assignedTargets.contains(position)
                            && isResponderTargetAvailable(terminal, position)) {
                        return Optional.of(position);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private int distanceFromAccident(GridPosition position, AccidentEncounter accident) {
        return Math.min(
                manhattanDistance(position, accident.first().getPosition()),
                manhattanDistance(position, accident.second().getPosition())
        );
    }

    private int manhattanDistance(GridPosition first, GridPosition second) {
        return Math.abs(first.row() - second.row()) + Math.abs(first.column() - second.column());
    }

    private boolean isResponderTargetAvailable(Terminal terminal, GridPosition position) {
        return movementsFor(terminal).stream()
                .noneMatch(movement -> movement.getPosition().equals(position));
    }

    private void returnResponderToDock(VesselMovement movement, List<String> events) {
        Vessel vessel = movement.getVessel();
        Optional<Terminal> returnTerminal = port.findTerminal(movement.getReturnTerminalId());
        if (returnTerminal.isEmpty()) {
            events.add(vessel.getName() + " je zavrsio intervenciju, ali povratni terminal nije pronadjen.");
            return;
        }

        Terminal terminal = returnTerminal.get();
        int returnDockIndex = movement.getReturnDockIndex();
        reservedDockIndexes.getOrDefault(terminal.getId(), Set.of()).remove(returnDockIndex);
        if (returnDockIndex >= 0 && returnDockIndex < terminal.getDockCapacity() && !terminal.isDockOccupied(returnDockIndex)) {
            terminal.addVessel(vessel, returnDockIndex);
        } else {
            terminal.addVessel(vessel);
        }
        events.add(vessel.getInstitution() + " " + vessel.getName()
                + " je zavrsio intervenciju i vratio se na terminal " + terminal.getId() + ".");
    }

    private void detectWantedVessels(List<String> events) {
        for (Terminal terminal : port.getTerminals()) {
            List<Vessel> coastGuards = terminal.getVessels().stream()
                    .filter(vessel -> vessel.getInstitution() == Institution.COAST_GUARD)
                    .filter(vessel -> !isInPendingWantedEscort(vessel))
                    .filter(vessel -> vessel.getWantedImoFilePath() != null && !vessel.getWantedImoFilePath().isBlank())
                    .toList();
            for (Vessel coastGuard : coastGuards) {
                Set<String> wanted = readWantedImoNumbers(coastGuard);
                terminal.getVessels().stream()
                        .filter(vessel -> !vessel.equals(coastGuard))
                        .filter(vessel -> !isInPendingWantedEscort(vessel))
                        .filter(vessel -> wanted.contains(normalizeImoNumber(vessel.getImoNumber())))
                        .filter(vessel -> handledWantedVessels.add(vessel.getId()))
                        .findFirst()
                        .ifPresent(vessel -> startWantedInvestigation(terminal, coastGuard, vessel, events));
            }
        }
    }

    private void startWantedInvestigation(Terminal terminal, Vessel coastGuard, Vessel wanted, List<String> events) {
        int seconds = ThreadLocalRandom.current().nextInt(3, 6);
        String fileInfo = InvestigationService.save("POTJERA", terminal.getId(), List.of(coastGuard, wanted))
                .map(path -> " Uvidjaj: " + path)
                .orElse("");
        long releaseAt = System.currentTimeMillis() + seconds * 1000L;
        vesselsInWantedInvestigation.add(coastGuard.getId());
        vesselsInWantedInvestigation.add(wanted.getId());
        pendingWantedEscorts.add(new PendingWantedEscort(terminal.getId(), coastGuard, wanted, releaseAt));
        events.add("Obalska straza " + coastGuard.getName()
                + " je detektovala plovilo " + wanted.getName()
                + ". Uvidjaj potjere traje " + seconds + " s."
                + " Saobracaj na terminalu se nastavlja." + fileInfo);
    }

    private void processPendingWantedEscorts(long now, List<String> events) {
        Iterator<PendingWantedEscort> iterator = pendingWantedEscorts.iterator();
        while (iterator.hasNext()) {
            PendingWantedEscort escort = iterator.next();
            if (escort.releaseAt() > now) {
                continue;
            }

            vesselsInWantedInvestigation.remove(escort.coastGuard().getId());
            vesselsInWantedInvestigation.remove(escort.wanted().getId());
            Optional<Terminal> terminal = port.findTerminal(escort.terminalId());
            if (terminal.isEmpty()) {
                events.add("Potjera nije pokrenuta jer terminal " + escort.terminalId() + " nije pronadjen.");
                iterator.remove();
                continue;
            }

            Terminal targetTerminal = terminal.get();
            escort.coastGuard().setRotationOn(true);
            boolean coastGuardPresent = targetTerminal.getVessels().contains(escort.coastGuard());
            boolean wantedPresent = targetTerminal.getVessels().contains(escort.wanted());
            if (coastGuardPresent && wantedPresent) {
                targetTerminal.getVessels().remove(escort.coastGuard());
                targetTerminal.getVessels().remove(escort.wanted());
                int rows = TerminalLayout.rowsFor(targetTerminal);
                VesselMovement coastGuardMovement = VesselMovement.leaving(escort.coastGuard(), rows);
                VesselMovement wantedMovement = VesselMovement.leaving(escort.wanted(), rows);
                registerWantedEscort(rows, coastGuardMovement, wantedMovement);
                addMovement(targetTerminal, coastGuardMovement);
                addMovement(targetTerminal, wantedMovement);
                events.add("Uvidjaj potjere je zavrsen. Obalska straza " + escort.coastGuard().getName()
                        + " prati plovilo " + escort.wanted().getName() + " ka izlazu iz luke.");
            } else {
                events.add("Potjera nije pokrenuta jer jedno od plovila vise nije na terminalu " + targetTerminal.getId() + ".");
            }
            iterator.remove();
        }
    }

    private boolean isInPendingWantedEscort(Vessel vessel) {
        return pendingWantedEscorts.stream()
                .anyMatch(escort -> escort.coastGuard().equals(vessel) || escort.wanted().equals(vessel));
    }

    private boolean isInWantedInvestigation(Vessel vessel) {
        return vesselsInWantedInvestigation.contains(vessel.getId());
    }

    private Set<String> readWantedImoNumbers(Vessel coastGuard) {
        try {
            return new HashSet<>(Files.readAllLines(java.nio.file.Path.of(coastGuard.getWantedImoFilePath())).stream()
                    .flatMap(line -> java.util.Arrays.stream(line.split("[,;\\s]+")))
                    .map(String::trim)
                    .map(SimulationService::normalizeImoNumber)
                    .filter(line -> !line.isEmpty())
                    .toList());
        } catch (IOException exception) {
            AppLogger.error("Neuspjesno citanje fajla potjernica.", exception);
            return Set.of();
        }
    }

    private static String normalizeImoNumber(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace("\uFEFF", "")
                .replace("\"", "")
                .replaceFirst("(?i)^IMO\\s*", "")
                .trim();
    }

    private String registerExit(Vessel vessel, int terminalId) {
        unregisterWantedEscort(vessel);
        LocalDateTime exitedAt = LocalDateTime.now();
        long tax = TaxService.calculateTax(vessel, exitedAt);
        if (tax > 0) {
            port.addTaxRecord(vessel, exitedAt, tax);
            return vessel.getName() + " izlazi sa terminala " + terminalId + ". Taksa: " + tax + " KM.";
        }
        return vessel.getName() + " izlazi sa terminala " + terminalId + ". Drzavno plovilo, bez naplate.";
    }

    private void registerWantedEscort(int rows, VesselMovement first, VesselMovement second) {
        VesselMovement leader = first.remainingDistanceToExit(rows) <= second.remainingDistanceToExit(rows)
                ? first
                : second;
        VesselMovement follower = leader == first ? second : first;
        wantedEscortFollowers.put(leader.getVessel().getId(), follower.getVessel().getId());
        wantedEscortLeaders.put(follower.getVessel().getId(), leader.getVessel().getId());
    }

    private boolean isWantedEscortPair(Vessel first, Vessel second) {
        if (first == null || second == null) {
            return false;
        }
        String firstId = first.getId();
        String secondId = second.getId();
        return secondId.equals(wantedEscortFollowers.get(firstId))
                || secondId.equals(wantedEscortLeaders.get(firstId));
    }

    private void unregisterWantedEscort(Vessel vessel) {
        String followerId = wantedEscortFollowers.remove(vessel.getId());
        if (followerId != null) {
            wantedEscortLeaders.remove(followerId);
        }
        String leaderId = wantedEscortLeaders.remove(vessel.getId());
        if (leaderId != null) {
            wantedEscortFollowers.remove(leaderId);
        }
    }

    private List<VesselMovement> movementsFor(Terminal terminal) {
        return movementsByTerminal.computeIfAbsent(terminal.getId(), key -> new ArrayList<>());
    }

    private void addMovement(Terminal terminal, VesselMovement movement) {
        movementsFor(terminal).add(movement);
        if (running) {
            assignMovementToVesselThread(movement);
        }
    }

    void removeMovement(VesselMovement movement) {
        movementsByTerminal.values().forEach(movements -> movements.remove(movement));
    }

    private void startVesselThreadsForAllVessels() {
        port.getTerminals().stream()
                .flatMap(terminal -> terminal.getVessels().stream())
                .forEach(this::startVesselThread);
        movementsByTerminal.values().stream()
                .flatMap(List::stream)
                .forEach(this::assignMovementToVesselThread);
    }

    private VesselThread startVesselThread(Vessel vessel) {
        VesselThread existing = vesselThreads.get(vessel.getId());
        if (existing != null) {
            return existing;
        }

        VesselThread thread = new VesselThread(this, vessel);
        vesselThreads.put(vessel.getId(), thread);
        thread.start();
        return thread;
    }

    private void assignMovementToVesselThread(VesselMovement movement) {
        if (!running || isInWantedInvestigation(movement.getVessel())) {
            return;
        }
        startVesselThread(movement.getVessel()).assignMovement(movement);
    }

    synchronized void unregisterVesselThread(VesselThread thread) {
        if (vesselThreads.get(thread.getVesselId()) == thread) {
            vesselThreads.remove(thread.getVesselId());
        }
    }

    private void stopMovementThreads() {
        running = false;
        paused = false;
        new ArrayList<>(vesselThreads.values()).forEach(VesselThread::stopMovement);
        vesselThreads.clear();
    }

    private void addEvent(String event) {
        eventQueue.add(event);
    }

    void addEvents(List<String> events) {
        events.forEach(this::addEvent);
    }

    private record ResponderLocation(Terminal terminal, Vessel vessel) {
    }

    private record AccidentEncounter(VesselMovement first, VesselMovement second) {
    }

    private record PendingWantedEscort(int terminalId, Vessel coastGuard, Vessel wanted, long releaseAt) {
    }
}
