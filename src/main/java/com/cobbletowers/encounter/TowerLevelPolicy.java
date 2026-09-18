package com.cobbletowers.encounter;

import com.cobbletowers.definition.RulesetDefinition;
import java.util.Collection;
import java.util.OptionalInt;

/**
 * The level a tower opponent fights at. The only place that decides one.
 *
 * <p>TDS #45 is explicit that rounding, bounds and floor adjustments are centralised and that level
 * maths is not scattered through encounter code. {@link TowerLevelSnapshot} owns the mean over the
 * registered parties; this adds everything that turns that mean into the number an opponent is
 * spawned at, and nothing else in the mod is allowed to do arithmetic on a level.
 *
 * <p>Taken once per floor. The party it was measured from can faint, disconnect or go and spectate
 * without the floor becoming easier underneath them.
 */
public final class TowerLevelPolicy {

    /** How much each floor adds on top of the party's own level. */
    public static final int PER_FLOOR_STEP = 1;

    private TowerLevelPolicy() {}

    /**
     * The level for one opponent.
     *
     * @param partyLevels every registered Pokemon of every participant, fainted ones included
     * @param floorIndex  1-based, so the first floor adds one step
     * @param entryOffset the pool entry's own offset, for a tougher opponent inside an ordinary pool
     * @param ruleset     supplies the bounds; a floor never produces a level outside them
     */
    public static OptionalInt levelFor(Collection<Integer> partyLevels, int floorIndex, int entryOffset,
                                       RulesetDefinition ruleset) {
        OptionalInt mean = TowerLevelSnapshot.of(partyLevels);
        if (mean.isEmpty()) return OptionalInt.empty();

        int scaled = mean.getAsInt() + floorIndex * PER_FLOOR_STEP + entryOffset;
        return OptionalInt.of(clamp(scaled, ruleset));
    }

    /**
     * Held inside the ruleset's bounds and then inside Cobblemon's.
     *
     * <p>Both, in that order: a ruleset asking for level 120 is a content mistake that should not
     * reach Cobblemon, and one asking for 0 should not reach it either.
     */
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
