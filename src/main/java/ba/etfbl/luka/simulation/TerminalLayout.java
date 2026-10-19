package ba.etfbl.luka.simulation;

import ba.etfbl.luka.model.Terminal;

public final class TerminalLayout {
    public static final int TOP_DOCK_ROW = 0;
    public static final int OUTBOUND_CHANNEL_ROW = 1;
    public static final int INBOUND_CHANNEL_ROW = 2;
    public static final int BOTTOM_DOCK_ROW = 3;

    private TerminalLayout() {
    }

    public static int rowsFor(Terminal terminal) {
        return Math.max(14, (int) Math.ceil(terminal.getDockCapacity() / 2.0));
    }

    public static GridPosition dockPosition(int dockIndex, int rows) {
        if (dockIndex < rows) {
            return new GridPosition(BOTTOM_DOCK_ROW, dockIndex);
        }
        return new GridPosition(TOP_DOCK_ROW, rows - 1 - (dockIndex - rows));
    }

    public static boolean isDockRow(int row) {
        return row == TOP_DOCK_ROW || row == BOTTOM_DOCK_ROW;
    }

    public static boolean isChannelRow(int row) {
        return row == OUTBOUND_CHANNEL_ROW || row == INBOUND_CHANNEL_ROW;
    }
}
