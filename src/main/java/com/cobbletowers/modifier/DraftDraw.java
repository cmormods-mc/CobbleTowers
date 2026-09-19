package com.cobbletowers.modifier;

import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.encounter.EncounterSeed;
import java.util.ArrayList;
import java.util.List;

/**
 * The cards an intermission puts on the table (TDS #2).
 *
 * <p>Deterministic from the run's seed, like the opponents and the boss, so a crash mid-draft cannot
 * reroll the offer (TDS #29). A party that did not like its three cards cannot get three others by
 * pulling the plug.
 *
 * <p><b>Its own ordinal space</b>, for the reason {@link com.cobbletowers.encounter.BossDraw} gives:
 * a shared space would let a party read one draw and predict another. Ordinary opponents use 0..n,
 * the boss uses 1_000_003, and a draft's cards start here.
 *
 * <p>Pure -- no server, no registry -- so the whole draw is tested without Minecraft.
 */
public final class DraftDraw {

    /** Far outside both the opponents' range and the boss's single ordinal. */
    static final int DRAFT_ORDINAL_BASE = 2_000_029;

    /** Three cards, as TDS #2 specifies. Fewer only when the pool cannot supply three. */
    public static final int CARDS = 3;

    private DraftDraw() {}

    /**
     * The cards offered at one floor's intermission.
     *
     * <p>Drawn <b>without replacement</b>: the same modifier twice on one table is not a choice.
     * A pool with fewer than three eligible entries offers what it has rather than padding with
     * duplicates, and an empty one offers nothing -- which the caller reads as "no draft here".
     *
     * @param pool every modifier this floor may offer, already filtered to what is eligible
     */
    public static List<ModifierDefinition> draw(List<ModifierDefinition> pool, long runSeed, int floorIndex) {
        List<ModifierDefinition> remaining = new ArrayList<>(pool);
        List<ModifierDefinition> cards = new ArrayList<>(CARDS);
        for (int card = 0; card < CARDS && !remaining.isEmpty(); card++) {
            long seed = EncounterSeed.of(runSeed, floorIndex, DRAFT_ORDINAL_BASE + card);
            cards.add(remaining.remove(pick(remaining, seed)));
        }
        return List.copyOf(cards);
    }

    /**
     * The weighted pick, as an index into {@code candidates}.
     *
     * <p>The same walk {@code EncounterDraw.pick} uses. Returning an index rather than an entry is
     * what lets the caller remove it and draw again without replacement.
     */
    static int pick(List<ModifierDefinition> candidates, long seed) {
        int total = 0;
        for (ModifierDefinition candidate : candidates) total += candidate.weight();
        int roll = (int) Math.floorMod(seed, total);
        for (int index = 0; index < candidates.size(); index++) {
            roll -= candidates.get(index).weight();
            if (roll < 0) return index;
        }
        return candidates.size() - 1;
    }
}
