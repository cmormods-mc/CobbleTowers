package com.cobbletowers.encounter;

import com.cobbletowers.definition.BossPoolDefinition;
import com.cobbletowers.definition.RulesetDefinition;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;

/**
 * Which CobbleRaids boss finishes a floor, and at what level.
 *
 * <p>Deterministic from the run's seed, like the ordinary opponents, so a crash cannot reroll the
 * boss either (TDS #29).
 *
 * <p><b>Drawn from a separate ordinal space.</b> If the boss were simply "opponent zero" of the same
 * sequence, a party could read the first ordinary encounter and know what was waiting at the end of
 * the floor -- the tower would leak its own surprise. The offset below is what keeps the two draws
 * independent while still coming from one seed.
 */
public final class BossDraw {

    /**
     * Far outside the ordinal range a floor's opponents ever use, so the two draws never collide.
     * A floor has at most four ordinary opponents; this is not a number that will be reached.
     */
    private static final int BOSS_ORDINAL = 1_000_003;

    private BossDraw() {}

    /** What this floor's boss is, and the level it fights at. */
    public record Boss(ResourceLocation definition, int level) {}

    /**
     * The boss for one floor of one run.
     *
     * @param handpicked a milestone's own definition; when present it is used and nothing is drawn
     */
    public static Optional<Boss> draw(Optional<BossPoolDefinition> pool, Optional<ResourceLocation> handpicked,
                                      long runSeed, int floorIndex, Collection<Integer> partyLevels,
                                      RulesetDefinition ruleset) {
        if (handpicked.isPresent()) {
            // Floors 5 and 10: special through content, not through a second code path.
            return TowerLevelPolicy.levelFor(partyLevels, floorIndex, 0, ruleset).stream()
                    .mapToObj(level -> new Boss(handpicked.get(), level))
                    .findFirst();
        }
        if (pool.isEmpty()) return Optional.empty();

        BossPoolDefinition.Entry entry = pick(pool.get(), EncounterSeed.of(runSeed, floorIndex, BOSS_ORDINAL));
        OptionalInt level = TowerLevelPolicy.levelFor(partyLevels, floorIndex, entry.levelOffset(), ruleset);
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
