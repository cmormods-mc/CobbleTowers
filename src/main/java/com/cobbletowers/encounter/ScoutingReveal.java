package com.cobbletowers.encounter;

import com.cobbletowers.definition.ScoutingProfileDefinition.RevealCategory;

/**
 * Whether one scouting category is visible on one floor (TDS #22, #49).
 *
 * <p>Pure, no Minecraft -- the fifth number in this codebase that moves with floor depth, after
 * level ({@link TowerLevelPolicy}), reward growth, jersey weighting ({@link RegionalWeighting}) and
 * jersey numbers. "Difficulty" has nothing else to attach to: floor depth is the one axis every other
 * scaling rule here already reads.
 */
public final class ScoutingReveal {

    private ScoutingReveal() {}

    /**
     * @param scoutingBonus from a run's drafted modifiers (TDS #49); pushes concealment deeper, never
     *                      un-conceals something a profile already decided to hide sooner
     */
    public static boolean isRevealed(RevealCategory category, int floorIndex, int scoutingBonus) {
        return category.concealedFromFloor() < 0 || floorIndex < category.concealedFromFloor() + scoutingBonus;
    }
}
