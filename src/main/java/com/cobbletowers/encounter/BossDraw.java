package com.cobbletowers.encounter;

import com.cobbletowers.definition.BossPoolDefinition;
import com.cobbletowers.definition.RulesetDefinition;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;

/**
 * Which CobbleRaids boss finishes a floor and at what level. Deterministic from the run seed (TDS #29), drawn from a
 * separate ordinal space so the first opponent does not reveal the boss.
 */
public final class BossDraw {

    /** Far outside any ordinal a floor's opponents use (at most four). */
    private static final int BOSS_ORDINAL = 1_000_003;

    private BossDraw() {}

    /** What this floor's boss is, and the level it fights at. */
    public record Boss(ResourceLocation definition, int level) {}

    /** The boss for one floor of one run. @param handpicked a milestone's own definition; used as is when present */
    public static Optional<Boss> draw(Optional<BossPoolDefinition> pool, Optional<ResourceLocation> handpicked,
                                      long runSeed, int floorIndex, Collection<Integer> partyLevels,
                                      RulesetDefinition ruleset) {
        return draw(pool, handpicked, runSeed, floorIndex, partyLevels, ruleset, 0);
    }

    /** The same draw, with a run's drafted modifiers folded into the level it is asked for. */
    public static Optional<Boss> draw(Optional<BossPoolDefinition> pool, Optional<ResourceLocation> handpicked,
                                      long runSeed, int floorIndex, Collection<Integer> partyLevels,
                                      RulesetDefinition ruleset, int modifierLevelOffset) {
        if (handpicked.isPresent()) {
            // Floors 5 and 10: special through content, not through a second code path.
            return TowerLevelPolicy.levelFor(partyLevels, floorIndex, modifierLevelOffset, ruleset).stream()
                    .mapToObj(level -> new Boss(handpicked.get(), level))
                    .findFirst();
        }
        if (pool.isEmpty()) return Optional.empty();

        BossPoolDefinition.Entry entry = pick(pool.get(), EncounterSeed.of(runSeed, floorIndex, BOSS_ORDINAL));
        OptionalInt level = TowerLevelPolicy.levelFor(partyLevels, floorIndex,
                entry.levelOffset() + modifierLevelOffset, ruleset);
        return level.stream().mapToObj(value -> new Boss(entry.definition(), value)).findFirst();
    }

    /** The same walk the encounter pool uses; see EncounterDraw.pick for why it is written this way. */
    static BossPoolDefinition.Entry pick(BossPoolDefinition pool, long seed) {
        int roll = (int) Math.floorMod(seed, pool.totalWeight());
        for (BossPoolDefinition.Entry entry : pool.entries()) {
            roll -= entry.weight();
            if (roll < 0) return entry;
        }
        return pool.entries().get(pool.entries().size() - 1);
    }
}
