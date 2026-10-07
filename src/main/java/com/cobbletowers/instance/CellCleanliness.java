package com.cobbletowers.instance;

import java.util.BitSet;

/**
 * Which cells are known to hold nothing (docs/design/cell-allocation-async.md, option C1), so {@link
 * CellPreparer#prepare} can skip scanning a just-cleared cell. In memory: after a restart every cell is unknown and
 * scanned once. A cell that may have changed is marked dirty.
 */
final class CellCleanliness {

    private static final BitSet CLEAN = new BitSet(CellGrid.MAX_CELLS);

    private CellCleanliness() {}

    static boolean isClean(int cell) {
        return cell >= 0 && cell < CellGrid.MAX_CELLS && CLEAN.get(cell);
    }

    static void markClean(int cell) {
        if (cell >= 0 && cell < CellGrid.MAX_CELLS) CLEAN.set(cell);
    }

    static void markDirty(int cell) {
        if (cell >= 0 && cell < CellGrid.MAX_CELLS) CLEAN.clear(cell);
    }

    /** Server stop, and a test seam. */
    static void clearAll() {
        CLEAN.clear();
    }
}
