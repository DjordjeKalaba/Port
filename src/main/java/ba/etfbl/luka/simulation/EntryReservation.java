package ba.etfbl.luka.simulation;

import java.util.List;

public record EntryReservation(DockReservation dockReservation, List<Integer> skippedTerminalIds) {
}
