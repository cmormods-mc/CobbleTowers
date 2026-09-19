package com.cobbletowers.encounter;

import com.cobbletowers.definition.EncounterPoolDefinition;
import com.cobbletowers.definition.RegionalThemeDefinition;
import java.util.Optional;

/**
 * How much heavier a jersey entry gets as a themed pool's floor deepens (TDS #73).
 *
 * <p>Pure, no Minecraft -- the one place this arithmetic exists, the way {@link TowerLevelPolicy}
 * is the one place level maths exists (TDS #45) and {@code RewardTableDefinition}'s growth step is
 * the one place reward value scales with depth. This is the third number in the codebase that grows
 * with floor index, and it must not become a third pattern for doing that.
 *
 * <p>No theme, or a pool that names none: every weight passes through unchanged, so an untouched pool
 * draws exactly as it always has.
 */
public final class RegionalWeighting {

    private RegionalWeighting() {}

    /** The weight one entry should actually be drawn with, on this floor, under this theme. */
    public static int weightFor(EncounterPoolDefinition.Entry entry, Optional<RegionalThemeDefinition> theme,
                                int floorIndex) {
        if (theme.isEmpty() || !theme.get().isJerseySpecies(entry.species())) return entry.weight();
        int growth = theme.get().jerseyWeightGrowthPercentPerFloor();
        return entry.weight() * (100 + growth * floorIndex) / 100;
    }

    /** The sum a draw should divide by -- {@link EncounterPoolDefinition#totalWeight()}, adjusted. */
    public static int totalWeight(EncounterPoolDefinition pool, Optional<RegionalThemeDefinition> theme,
                                  int floorIndex) {
        int total = 0;
        for (EncounterPoolDefinition.Entry entry : pool.entries()) {
            total += weightFor(entry, theme, floorIndex);
        }
        return total;
    }
}
