package com.cobbletowers.encounter;

import com.cobbletowers.definition.RulesetDefinition;
import java.util.Collection;
import java.util.OptionalInt;

/**
 * The level a tower opponent fights at, and the only place that decides one (TDS #45). {@link TowerLevelSnapshot}
 * owns the mean over registered parties; this turns it into the spawn level. Taken once per floor.
 */
public final class TowerLevelPolicy {

    /** How much each floor adds on top of the party's own level. */
    public static final int PER_FLOOR_STEP = 1;

    private TowerLevelPolicy() {}

    /**
     * The level for one opponent.
     * @param partyLevels every registered Pokemon of every participant, fainted included
     * @param floorIndex 1-based
     * @param entryOffset the pool entry's own offset
     * @param ruleset supplies the bounds
     */
    public static OptionalInt levelFor(Collection<Integer> partyLevels, int floorIndex, int entryOffset,
                                       RulesetDefinition ruleset) {
        OptionalInt mean = TowerLevelSnapshot.of(partyLevels);
        if (mean.isEmpty()) return OptionalInt.empty();

        int scaled = mean.getAsInt() + floorIndex * PER_FLOOR_STEP + entryOffset;
        return OptionalInt.of(clamp(scaled, ruleset));
    }

    /** Held inside the ruleset's bounds, then Cobblemon's. */
    public static int clamp(int level, RulesetDefinition ruleset) {
        int low = Math.max(ruleset.minEnemyLevel(), TowerLevelSnapshot.MIN_LEVEL);
        int high = Math.min(ruleset.maxEnemyLevel(), TowerLevelSnapshot.MAX_LEVEL);
        if (low > high) {
            // Content says min above max. Refusing would take a floor down over a typo, so the
            // ruleset's own minimum wins and the definition validator is what complains about it.
            return Math.max(TowerLevelSnapshot.MIN_LEVEL, Math.min(low, TowerLevelSnapshot.MAX_LEVEL));
        }
        return Math.max(low, Math.min(high, level));
    }
}
