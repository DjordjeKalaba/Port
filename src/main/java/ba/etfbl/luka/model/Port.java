package ba.etfbl.luka.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Port implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<Terminal> terminals;
    private List<PortTaxRecord> taxRecords;
    private List<PortEntryRecord> entryRecords;

    public Port(int terminalCount, int dockCapacity) {
        this.terminals = new ArrayList<>();
        this.taxRecords = new ArrayList<>();
        this.entryRecords = new ArrayList<>();
        for (int i = 1; i <= terminalCount; i++) {
            terminals.add(new Terminal(i, dockCapacity));
        }
    }

    public List<Terminal> getTerminals() {
        return terminals;
    }

    public List<PortTaxRecord> getTaxRecords() {
        if (taxRecords == null) {
            taxRecords = new ArrayList<>();
        }
        return taxRecords;
    }

    public List<PortEntryRecord> getEntryRecords() {
        if (entryRecords == null) {
            entryRecords = new ArrayList<>();
        }
        return entryRecords;
    }

    public Optional<Terminal> findTerminal(int terminalId) {
        return terminals.stream()
                .filter(terminal -> terminal.getId() == terminalId)
                .findFirst();
    }

    public boolean hasFreeDock() {
        return terminals.stream().anyMatch(Terminal::hasFreeDock);
    }

    public Optional<Terminal> firstTerminalWithFreeDock() {
        return terminals.stream()
                .filter(Terminal::hasFreeDock)
                .min(Comparator.comparingInt(Terminal::getId));
    }

    public void ensureTerminalCount(int terminalCount, int dockCapacity) {
        terminals.forEach(Terminal::normalizeDockIndexes);
        getTaxRecords();
        getEntryRecords();
        int nextId = terminals.size() + 1;
        while (terminals.size() < terminalCount) {
            terminals.add(new Terminal(nextId++, dockCapacity));
        }
    }

    public void addTaxRecord(Vessel vessel, LocalDateTime exitedAt, long amount) {
        getTaxRecords().add(new PortTaxRecord(
                vessel.getImoNumber(),
                vessel.getName(),
                vessel.getEnteredAt(),
                exitedAt,
                amount
        ));
    }

    public void recordEntry(Vessel vessel) {
        boolean alreadyRecorded = getEntryRecords().stream()
                .anyMatch(record -> record.imoNumber() != null
                        && record.imoNumber().equalsIgnoreCase(vessel.getImoNumber())
                        && record.enteredAt().equals(vessel.getEnteredAt()));
        if (alreadyRecorded) {
            return;
        }
        getEntryRecords().add(new PortEntryRecord(
                vessel.getImoNumber(),
                vessel.getName(),
                vessel.getTerminalId(),
                vessel.getEnteredAt()
        ));
    }
}
