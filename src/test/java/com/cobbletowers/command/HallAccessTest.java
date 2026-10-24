package com.cobbletowers.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HallAccessTest {

    @Test
    @DisplayName("a plain player with no team or run cannot open the Hall by typing")
    void plainPlayer() {
        assertFalse(HallAccess.mayTypeIt(false, false, false));
    }

    @Test
    @DisplayName("operators, team members and runners keep /tower")
    void others() {
        assertTrue(HallAccess.mayTypeIt(true, false, false));
        assertTrue(HallAccess.mayTypeIt(false, true, false));
        assertTrue(HallAccess.mayTypeIt(false, false, true));
    }
}
