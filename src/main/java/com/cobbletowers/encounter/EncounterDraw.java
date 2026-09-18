package com.cobbletowers.encounter;

import com.cobbletowers.definition.EncounterPoolDefinition;
import com.cobbletowers.definition.RulesetDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Which opponents a floor puts up, drawn from its pool.
 *
 * <p>Deterministic: the same run on the same floor always meets the same opponents in the same
 * order, because every choice comes from {@link EncounterSeed} rather than from a random source
 * (TDS #29). That is what lets a crashed run be resumed without anybody being able to reroll a floor
 * they did not like.
 *
 * <p>Pure -- no server, no world -- so the whole draw is tested without Minecraft.
 */
public final class EncounterDraw {

    private EncounterDraw() {}

    /**
     * One opponent for one player on one floor.
     *
     * @param ordinal which opponent of the floor this is; each player on a floor gets their own
     */
    public static Optional<EncounterSnapshot> draw(EncounterPoolDefinition pool, long runSeed, int floorIndex,
                                                   int ordinal, Collection<Integer> partyLevels,
                                                   RulesetDefinition ruleset) {
        EncounterPoolDefinition.Entry entry = pick(pool, EncounterSeed.of(runSeed, floorIndex, ordinal));
        return TowerLevelPolicy.levelFor(partyLevels, floorIndex, entry.levelOffset(), ruleset).stream()
                .mapToObj(level -> new EncounterSnapshot(ordinal, entry.species(), entry.aspects(), level))
                .findFirst();
    }

    /** A floor's whole round: one opponent per player, in a stable order. */
    public static List<EncounterSnapshot> drawRound(EncounterPoolDefinition pool, long runSeed, int floorIndex,
                                                    int opponents, Collection<Integer> partyLevels,
                                                    RulesetDefinition ruleset) {
        List<EncounterSnapshot> round = new ArrayList<>(Math.max(opponents, 0));
        for (int ordinal = 0; ordinal < opponents; ordinal++) {
            draw(pool, runSeed, floorIndex, ordinal, partyLevels, ruleset).ifPresent(round::add);
        }
        return List.copyOf(round);
    }

    /**
     * The weighted pick itself.
     *
     * <p>Walks the entries subtracting weights, in the order the pool declares them.
     *
     * <p><b>Editing a pool changes what every seed draws.</b> The roll is taken modulo the total
     * weight, so adding an entry shifts all of them -- and no weighted pick can avoid that, since new
     * weight has to take probability from somewhere. The protection against a pool edited under an
     * in-flight run is not here: a run pins the content digest it started with (TDS #40), which makes
     * the edit visible rather than silent.
     */
    static EncounterPoolDefinition.Entry pick(EncounterPoolDefinition pool, long seed) {
        int total = pool.totalWeight();
        // Math.floorMod, not %, because a negative seed would otherwise index backwards off the end.
        int roll = (int) Math.floorMod(seed, total);
        for (EncounterPoolDefinition.Entry entry : pool.entries()) {
            roll -= entry.weight();
            if (roll < 0) return entry;
        }
        // Unreachable while weights are positive, which the record enforces; still, a pool is content.
        return pool.entries().get(pool.entries().size() - 1);
    }
}
