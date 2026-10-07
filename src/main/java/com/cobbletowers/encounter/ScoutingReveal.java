package com.cobbletowers.encounter;

import com.cobbletowers.definition.ScoutingProfileDefinition.RevealCategory;

/** Whether one scouting category is visible on one floor (TDS #22, #49). Pure. */
public final class ScoutingReveal {

    private ScoutingReveal() {}

    /** @param scoutingBonus from drafted modifiers (TDS #49); pushes concealment deeper, never earlier */
    public static boolean isRevealed(RevealCategory category, int floorIndex, int scoutingBonus) {
        return category.concealedFromFloor() < 0 || floorIndex < category.concealedFromFloor() + scoutingBonus;
    }
}
