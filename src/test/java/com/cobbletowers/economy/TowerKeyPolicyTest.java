package com.cobbletowers.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerKeyPolicyTest {

    @Test
    @DisplayName("only an ordinary run costs a key, and only when the operator has turned keys on")
    void whichRunsCostAKey() {
        assertTrue(TowerKeyPolicy.costsKey(true, false, false));
        assertFalse(TowerKeyPolicy.costsKey(true, true, false), "trials have their own gating");
        assertFalse(TowerKeyPolicy.costsKey(true, false, true), "a rental draft is already an investment");
        assertFalse(TowerKeyPolicy.costsKey(false, false, false), "off means off");
    }

    @Test
    @DisplayName("keys are off unless the config says required, so a missing or empty file locks nobody out")
    void offByDefault() {
        assertFalse(TowerKeyPolicy.parseRequired(null));
        assertFalse(TowerKeyPolicy.parseRequired(""));
        assertFalse(TowerKeyPolicy.parseRequired("{}"));
        assertFalse(TowerKeyPolicy.parseRequired("{\"required\": false}"));
        assertTrue(TowerKeyPolicy.parseRequired("{\"required\": true}"));
    }

    @Test
    @DisplayName("a malformed file throws, which the caller turns into not required")
    void malformedThrows() {
        assertThrows(RuntimeException.class, () -> TowerKeyPolicy.parseRequired("[1, 2]"));
    }
}
