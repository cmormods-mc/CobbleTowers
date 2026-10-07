package com.cobbletowers.modifier;

import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.encounter.EncounterSeed;
import java.util.ArrayList;
import java.util.List;

/**
 * The cards an intermission puts on the table (TDS #2). Deterministic from the run seed (TDS #29), in its own ordinal
 * space (see {@link com.cobbletowers.encounter.BossDraw}). Pure.
 */
public final class DraftDraw {

    /** Far outside both the opponents' range and the boss's single ordinal. */
    static final int DRAFT_ORDINAL_BASE = 2_000_029;

    /** The relic draw's own ordinal space (P34), for the same reason. */
    static final int RELIC_ORDINAL_BASE = 4_000_037;

    /** Three cards, as TDS #2 specifies. Fewer only when the pool cannot supply three. */
    public static final int CARDS = 3;

    private DraftDraw() {}

    /**
     * The cards offered at one floor's intermission, drawn without replacement. Fewer than three eligible offers what
     * exists; none means no draft.
     * @param pool every modifier this floor may offer, already filtered
     */
    public static List<ModifierDefinition> draw(List<ModifierDefinition> pool, long runSeed, int floorIndex) {
        return draw(pool, runSeed, floorIndex, DRAFT_ORDINAL_BASE);
    }

    /** The relics offered after a milestone boss: the same draw from its own ordinal space. */
    public static List<ModifierDefinition> drawRelics(List<ModifierDefinition> pool, long runSeed, int floorIndex) {
        return draw(pool, runSeed, floorIndex, RELIC_ORDINAL_BASE);
    }

    private static List<ModifierDefinition> draw(List<ModifierDefinition> pool, long runSeed, int floorIndex,
                                                 int ordinalBase) {
        List<ModifierDefinition> remaining = new ArrayList<>(pool);
        List<ModifierDefinition> cards = new ArrayList<>(CARDS);
        for (int card = 0; card < CARDS && !remaining.isEmpty(); card++) {
            long seed = EncounterSeed.of(runSeed, floorIndex, ordinalBase + card);
            cards.add(remaining.remove(pick(remaining, seed)));
        }
        return List.copyOf(cards);
    }

    /**
     * The weighted pick as an index into {@code candidates}, so the caller can remove it and draw again. Same walk as
     * {@code EncounterDraw.pick}.
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
