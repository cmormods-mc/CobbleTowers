package com.cobbletowers.instance;

import java.util.BitSet;

/**
 * Which cells are known to hold nothing (docs/design/cell-allocation-async.md, option C1). {@link CellPreparer#reset} leaves a cell clean, and
 * pasting a floor into it makes it dirty again, so {@link CellPreparer#prepare} can skip the scan of all 49 chunks when it is handed a cell that a
 * release has just cleared. In memory only: after a restart nothing remembers, every cell is unknown, and the first use of each is scanned once.
 *
 * <p>A cell whose contents may have changed behind our back (a quarantine that was cleared by hand, a failed verification) is marked dirty.
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
