package com.cobbletowers.reward;

import com.cobbletowers.definition.RewardTableDefinition;
import com.cobbletowers.encounter.EncounterSeed;
import java.util.List;

/**
 * The weighted item and the amount one grant rolls.
 *
 * <p><b>Its own ordinal space</b>, for the same reason {@link com.cobbletowers.encounter.BossDraw}
 * and {@code DraftDraw} give theirs: ordinary opponents use 0..n, the boss uses 1_000_003, a draft's
 * cards start at 2_000_029, and a reward's item picks start here. The amount roll for the same grant
 * uses a further, disjoint offset, so the two are independent draws rather than the same seed read
 * twice.
 *
 * <p>Deterministic from the run's seed, like every other draw, so a crash cannot reroll a grant
 * already priced (TDS #29).
 */
public final class RewardDraw {

    /** Far outside every other draw's ordinal range. */
    static final int REWARD_ORDINAL_BASE = 3_000_003;

    /** A disjoint sub-space for the amount roll, so it never reads the same seed as the item pick. */
    static final int AMOUNT_ORDINAL_OFFSET = 500_000;

    private RewardDraw() {}

    /** The {@code n}th item picked for one floor of one run. */
    public static RewardTableDefinition.Entry pickItem(long runSeed, int floorIndex, int n,
                                                        List<RewardTableDefinition.Entry> pool) {
        return pick(pool, EncounterSeed.of(runSeed, floorIndex, REWARD_ORDINAL_BASE + n));
    }

    /**
     * As above, with each entry's weight scaled by {@code weightPercent} (P36c: the season's spotlight region drops its armor more often).
     * 100 changes nothing; the same seed walks the same way, only over different widths.
     */
    public static RewardTableDefinition.Entry pickItem(long runSeed, int floorIndex, int n, List<RewardTableDefinition.Entry> pool,
                                                        java.util.function.ToIntFunction<RewardTableDefinition.Entry> weightPercent) {
        return pick(pool, EncounterSeed.of(runSeed, floorIndex, REWARD_ORDINAL_BASE + n), weightPercent);
    }

    /** The {@code n}th amount rolled for one floor of one run, within {@code [min, max]}. */
    public static int rollAmount(long runSeed, int floorIndex, int n, int min, int max) {
        long seed = EncounterSeed.of(runSeed, floorIndex, REWARD_ORDINAL_BASE + AMOUNT_ORDINAL_OFFSET + n);
        return min + (int) Math.floorMod(seed, (max - min + 1));
    }

    /** The same weighted walk {@code EncounterDraw.pick} uses. */
    static RewardTableDefinition.Entry pick(List<RewardTableDefinition.Entry> pool, long seed) {
        return pick(pool, seed, entry -> 100);
    }

    /** The scaled width of an entry: its weight times the percent, never below 1 (a weighted entry stays possible). */
    public static int scaled(RewardTableDefinition.Entry entry, java.util.function.ToIntFunction<RewardTableDefinition.Entry> weightPercent) {
        return Math.max(1, entry.weight() * Math.max(0, weightPercent.applyAsInt(entry)) / 100);
    }

    static RewardTableDefinition.Entry pick(List<RewardTableDefinition.Entry> pool, long seed,
                                            java.util.function.ToIntFunction<RewardTableDefinition.Entry> weightPercent) {
        int total = 0;
        for (RewardTableDefinition.Entry entry : pool) total += scaled(entry, weightPercent);
        int roll = (int) Math.floorMod(seed, total);
        for (RewardTableDefinition.Entry entry : pool) {
            roll -= scaled(entry, weightPercent);
            if (roll < 0) return entry;
        }
        return pool.get(pool.size() - 1);
    }
}
