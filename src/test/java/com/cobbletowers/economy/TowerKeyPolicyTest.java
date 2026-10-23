package com.cobbletowers.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerKeyPolicyTest {

    @Test
    @DisplayName("an ordinary run costs a tower key, a rental run a rental key, a trial nothing, and off means off")
    void whichKeyARunCosts() {
        assertEquals(Optional.of(TowerKeys.Kind.TOWER), TowerKeyPolicy.keyFor(true, false, false));
        assertEquals(Optional.of(TowerKeys.Kind.RENTAL), TowerKeyPolicy.keyFor(true, false, true));
        assertEquals(Optional.empty(), TowerKeyPolicy.keyFor(true, true, false), "trials have their own gating");
        assertEquals(Optional.empty(), TowerKeyPolicy.keyFor(false, false, false), "off means off");
        assertEquals(Optional.empty(), TowerKeyPolicy.keyFor(false, false, true), "off means off for rentals too");
    }

    @Test
    @DisplayName("keys are required unless the config says otherwise")
    void onByDefault() {
        assertTrue(TowerKeyPolicy.parseRequired(null));
        assertTrue(TowerKeyPolicy.parseRequired(""));
        assertTrue(TowerKeyPolicy.parseRequired("{}"));
        assertFalse(TowerKeyPolicy.parseRequired("{\"required\": false}"));
        assertTrue(TowerKeyPolicy.parseRequired("{\"required\": true}"));
    }

    @Test
    @DisplayName("a malformed file throws, which the caller turns into keeping the current setting")
    void malformedThrows() {
        assertThrows(RuntimeException.class, () -> TowerKeyPolicy.parseRequired("[1, 2]"));
    }

    @Test
    @DisplayName("the free run is back on a new day, gone for the rest of today, and forgiving when the stored day is odd")
    void freeRun() {
        assertTrue(TowerKeyPolicy.freeRunAvailable(Optional.empty(), "2026-10-23"));
        assertTrue(TowerKeyPolicy.freeRunAvailable(Optional.of("2026-10-22"), "2026-10-23"));
        assertFalse(TowerKeyPolicy.freeRunAvailable(Optional.of("2026-10-23"), "2026-10-23"));
        assertTrue(TowerKeyPolicy.freeRunAvailable(Optional.of("2026-10-25"), "2026-10-23"), "clock moved back");
        assertTrue(TowerKeyPolicy.freeRunAvailable(Optional.of("garbage"), "2026-10-23"));
    }
}
