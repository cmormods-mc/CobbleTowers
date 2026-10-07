package com.cobbletowers.encounter;

import com.cobbletowers.definition.EncounterPoolDefinition;
import com.cobbletowers.definition.RegionalThemeDefinition;
import java.util.Optional;

/**
 * How much heavier a jersey entry gets as a themed pool's floor deepens (TDS #73). Pure; the one place this
 * arithmetic exists. No theme, or a pool that names none, leaves weights unchanged.
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
