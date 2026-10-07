package com.cobbletowers.instance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CellCleanlinessTest {

    @BeforeEach
    void clean() {
        CellCleanliness.clearAll();
    }

    @Test
    @DisplayName("every cell is unknown at first: nothing is assumed clean after a restart")
    void unknownAtStart() {
        assertFalse(CellCleanliness.isClean(0));
        assertFalse(CellCleanliness.isClean(CellGrid.MAX_CELLS - 1));
    }

    @Test
    @DisplayName("a reset marks a cell clean and a build or a doubtful verification makes it dirty again")
    void lifecycle() {
        CellCleanliness.markClean(5);
        assertTrue(CellCleanliness.isClean(5));
        assertFalse(CellCleanliness.isClean(6), "only that cell");
        CellCleanliness.markDirty(5);
        assertFalse(CellCleanliness.isClean(5));
    }

    @Test
    @DisplayName("out-of-range cells are never clean and never throw")
    void outOfRange() {
        CellCleanliness.markClean(-1);
        CellCleanliness.markClean(CellGrid.MAX_CELLS);
        assertFalse(CellCleanliness.isClean(-1));
        assertFalse(CellCleanliness.isClean(CellGrid.MAX_CELLS));
        CellCleanliness.markDirty(-1);
    }

    @Test
    @DisplayName("clearing forgets everything (server stop)")
    void clearAll() {
        CellCleanliness.markClean(1);
        CellCleanliness.markClean(2);
        CellCleanliness.clearAll();
        assertFalse(CellCleanliness.isClean(1));
        assertFalse(CellCleanliness.isClean(2));
    }
}
