package ba.etfbl.luka.simulation;

import ba.etfbl.luka.model.Vessel;

public final class VesselMovement {
    private final Vessel vessel;
    private final MovementDirection direction;
    private final int targetDockIndex;
    private final GridPosition targetPosition;
    private final int returnTerminalId;
    private final int returnDockIndex;
    private GridPosition position;
    private Integer overtakingReturnRow;
    private int overtakingColumnStep;
    private long lastMovedAt;

    private VesselMovement(
            Vessel vessel,
            MovementDirection direction,
            int targetDockIndex,
            GridPosition targetPosition,
            int returnTerminalId,
            int returnDockIndex,
            GridPosition position
    ) {
        this.vessel = vessel;
        this.direction = direction;
        this.targetDockIndex = targetDockIndex;
        this.targetPosition = targetPosition;
        this.returnTerminalId = returnTerminalId;
        this.returnDockIndex = returnDockIndex;
        this.position = position;
    }

    public static VesselMovement entering(Vessel vessel, int targetDockIndex) {
        return new VesselMovement(
                vessel,
                MovementDirection.ENTERING,
                targetDockIndex,
                null,
                0,
                -1,
                new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, 0)
        );
    }

    public static VesselMovement leaving(Vessel vessel, int rows) {
        return new VesselMovement(
                vessel,
                MovementDirection.LEAVING,
                vessel.getDockIndex(),
                null,
                0,
                -1,
                TerminalLayout.dockPosition(vessel.getDockIndex(), rows)
        );
    }

    public static VesselMovement leavingFromPosition(Vessel vessel, GridPosition position) {
        return new VesselMovement(
                vessel,
                MovementDirection.LEAVING,
                vessel.getDockIndex(),
                null,
                0,
                -1,
                position
        );
    }

    public static VesselMovement responding(
            Vessel vessel,
            GridPosition targetPosition,
            int returnTerminalId,
            int returnDockIndex,
            GridPosition startPosition
    ) {
        return new VesselMovement(
                vessel,
                MovementDirection.RESPONDING,
                -1,
                targetPosition,
                returnTerminalId,
                returnDockIndex,
                startPosition
        );
    }

    public Vessel getVessel() {
        return vessel;
    }

    public MovementDirection getDirection() {
        return direction;
    }

    public int getTargetDockIndex() {
        return targetDockIndex;
    }

    public GridPosition getTargetPosition() {
        return targetPosition;
    }

    public int getReturnTerminalId() {
        return returnTerminalId;
    }

    public int getReturnDockIndex() {
        return returnDockIndex;
    }

    public GridPosition getPosition() {
        return position;
    }

    public long getLastMovedAt() {
        return lastMovedAt;
    }

    public void moveTo(GridPosition position, long movedAt) {
        this.position = position;
        this.lastMovedAt = movedAt;
        if (overtakingReturnRow != null && position.row() == overtakingReturnRow) {
            overtakingReturnRow = null;
            overtakingColumnStep = 0;
        }
    }

    public void startOvertaking(GridPosition position, GridPosition returnPosition, long movedAt) {
        this.position = position;
        this.overtakingReturnRow = returnPosition.row();
        this.overtakingColumnStep = returnPosition.column() - position.column();
        this.lastMovedAt = movedAt;
    }

    public void bypassTo(GridPosition position, long movedAt) {
        this.position = position;
        this.overtakingReturnRow = null;
        this.overtakingColumnStep = 0;
        this.lastMovedAt = movedAt;
    }

    public boolean isOvertaking() {
        return overtakingReturnRow != null;
    }

    public int remainingDistanceToExit(int rows) {
        if (position.row() == TerminalLayout.TOP_DOCK_ROW) {
            return position.column() + 1;
        }
        if (position.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW) {
            return position.column();
        }
        if (position.row() == TerminalLayout.BOTTOM_DOCK_ROW) {
            return 2 * rows - position.column();
        }
        if (position.row() == TerminalLayout.INBOUND_CHANNEL_ROW) {
            return 2 * rows - position.column() - 1;
        }
        return Integer.MAX_VALUE;
    }

    public GridPosition nextPosition(int rows) {
        if (direction == MovementDirection.ENTERING) {
            return nextEnteringPosition(rows);
        }
        if (direction == MovementDirection.RESPONDING) {
            return nextRespondingPosition(rows);
        }
        return nextLeavingPosition(rows);
    }

    public GridPosition skipBlockedPosition(int rows) {
        if (direction != MovementDirection.RESPONDING || isOvertaking()) {
            return null;
        }

        GridPosition blocked = nextRespondingPosition(position, rows);
        if (blocked == null || blocked.equals(targetPosition)) {
            return null;
        }
        return nextRespondingPosition(blocked, rows);
    }

    public GridPosition overtakingReturnPosition(int rows) {
        if (!isOvertaking()) {
            return null;
        }
        int column = position.column() + overtakingColumnStep;
        if (column < 0 || column >= rows) {
            return null;
        }
        return new GridPosition(overtakingReturnRow, column);
    }

    public GridPosition overtakingForwardPosition(int rows) {
        if (!isOvertaking()) {
            return null;
        }
        int column = position.column() + overtakingColumnStep;
        int nextReturnColumn = column + overtakingColumnStep;
        if (column < 0 || column >= rows || nextReturnColumn < 0 || nextReturnColumn >= rows) {
            return null;
        }
        return new GridPosition(position.row(), column);
    }

    public GridPosition overtakingPosition(int rows) {
        if (direction == MovementDirection.ENTERING
                && position.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && position.column() < lastInboundColumnBeforeTurn(rows) - 1) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, position.column() + 1);
        }
        if (direction == MovementDirection.LEAVING
                && position.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && position.column() > 1) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, position.column() - 1);
        }
        if (direction == MovementDirection.RESPONDING
                && position.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && position.column() < rows - 2) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, position.column() + 1);
        }
        if (direction == MovementDirection.RESPONDING
                && position.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && position.column() > 1) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, position.column() - 1);
        }
        return null;
    }

    public GridPosition initialOvertakingReturnPosition(int rows) {
        if (direction == MovementDirection.ENTERING
                && position.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && position.column() < lastInboundColumnBeforeTurn(rows) - 1) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, position.column() + 2);
        }
        if (direction == MovementDirection.LEAVING
                && position.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && position.column() > 1) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, position.column() - 2);
        }
        if (direction == MovementDirection.RESPONDING
                && position.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && position.column() < rows - 2) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, position.column() + 2);
        }
        if (direction == MovementDirection.RESPONDING
                && position.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && position.column() > 1) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, position.column() - 2);
        }
        return null;
    }

    private GridPosition nextEnteringPosition(int rows) {
        GridPosition dock = TerminalLayout.dockPosition(targetDockIndex, rows);
        if (position.row() == TerminalLayout.INBOUND_CHANNEL_ROW && position.column() < lastInboundColumnBeforeTurn(rows)) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, position.column() + 1);
        }
        if (position.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && position.column() == dock.column()
                && dock.row() == TerminalLayout.BOTTOM_DOCK_ROW) {
            return dock;
        }
        if (position.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && position.column() == rows - 1
                && dock.row() == TerminalLayout.TOP_DOCK_ROW) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, position.column());
        }
        if (position.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && position.column() > dock.column()
                && dock.row() == TerminalLayout.TOP_DOCK_ROW) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, position.column() - 1);
        }
        if (position.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && position.column() == dock.column()
                && dock.row() == TerminalLayout.TOP_DOCK_ROW) {
            return dock;
        }
        if (position.equals(dock)) {
            return null;
        }
        return null;
    }

    private GridPosition nextLeavingPosition(int rows) {
        if (position.row() == TerminalLayout.TOP_DOCK_ROW) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, position.column());
        }
        if (position.row() == TerminalLayout.BOTTOM_DOCK_ROW) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, position.column());
        }
        if (position.row() == TerminalLayout.INBOUND_CHANNEL_ROW) {
            if (position.column() < rows - 1) {
                return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, position.column() + 1);
            }
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, position.column());
        }
        if (position.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW && position.column() > 0) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, position.column() - 1);
        }
        return null;
    }

    private GridPosition nextRespondingPosition(int rows) {
        return nextRespondingPosition(position, rows);
    }

    private GridPosition nextRespondingPosition(GridPosition current, int rows) {
        if (current.equals(targetPosition)) {
            return null;
        }
        if (current.row() == TerminalLayout.TOP_DOCK_ROW) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, current.column());
        }
        if (current.row() == TerminalLayout.BOTTOM_DOCK_ROW) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, current.column());
        }
        if (targetPosition.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.column() < targetPosition.column()) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, current.column() + 1);
        }
        if (targetPosition.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.column() == targetPosition.column()) {
            return targetPosition;
        }
        if (targetPosition.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.column() < rows - 1) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, current.column() + 1);
        }
        if (targetPosition.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.column() == rows - 1) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, current.column());
        }
        if (targetPosition.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && current.column() > targetPosition.column()) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, current.column() - 1);
        }
        if (targetPosition.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && current.column() == targetPosition.column()) {
            return targetPosition;
        }
        if (targetPosition.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && current.column() < targetPosition.column()) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, current.column());
        }
        if (targetPosition.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.column() < rows - 1) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, current.column() + 1);
        }
        if (targetPosition.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.INBOUND_CHANNEL_ROW
                && current.column() == rows - 1) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, current.column());
        }
        if (targetPosition.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && current.column() > targetPosition.column()) {
            return new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, current.column() - 1);
        }
        if (targetPosition.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && current.column() == targetPosition.column()) {
            return targetPosition;
        }
        if (targetPosition.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && current.row() == TerminalLayout.OUTBOUND_CHANNEL_ROW
                && current.column() < targetPosition.column()) {
            return new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, current.column());
        }
        return null;
    }

    private int lastInboundColumnBeforeTurn(int rows) {
        GridPosition dock = TerminalLayout.dockPosition(targetDockIndex, rows);
        if (dock.row() == TerminalLayout.TOP_DOCK_ROW) {
            return rows - 1;
        }
        return dock.column();
    }
}
