package com.cobbletowers.encounter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.ScoutingProfileDefinition.RevealCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TDS #22/#49: floor depth is "difficulty" for concealment, and a scouting modifier pushes it back. */
class ScoutingRevealTest {

    @Test
    @DisplayName("a category with no concealment floor is always revealed")
    void neverConcealed() {
        RevealCategory category = new RevealCategory("typing", -1);

        assertTrue(ScoutingReveal.isRevealed(category, 1, 0));
        assertTrue(ScoutingReveal.isRevealed(category, 10, 0));
    }

    @Test
    @DisplayName("revealed before its concealment floor, hidden from it on")
    void concealsAtItsFloor() {
        RevealCategory category = new RevealCategory("threat_level", 6);

        assertTrue(ScoutingReveal.isRevealed(category, 5, 0));
        assertFalse(ScoutingReveal.isRevealed(category, 6, 0));
        assertFalse(ScoutingReveal.isRevealed(category, 9, 0));
    }

    @Test
    @DisplayName("a scouting bonus pushes concealment deeper, never un-conceals something hidden sooner")
    void bonusPushesConcealmentDeeper() {
        RevealCategory category = new RevealCategory("field_conditions", 6);

        assertFalse(ScoutingReveal.isRevealed(category, 6, 0), "concealed at floor 6 with no bonus");
        assertTrue(ScoutingReveal.isRevealed(category, 6, 2), "a +2 bonus keeps it visible through floor 7");
        assertFalse(ScoutingReveal.isRevealed(category, 8, 2), "but not forever");
    }
}
