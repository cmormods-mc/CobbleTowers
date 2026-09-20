package com.cobbletowers.encounter;

import com.cobbletowers.definition.EncounterPoolDefinition;
import com.cobbletowers.definition.RegionalThemeDefinition;
import com.cobbletowers.definition.RulesetDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

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
        return draw(pool, runSeed, floorIndex, ordinal, partyLevels, ruleset, 0);
    }

    /**
     * The same draw, with a run's drafted modifiers folded in.
     *
     * <p>{@code modifierLevelOffset} is added to the pool entry's own offset and the sum is handed to
     * {@link TowerLevelPolicy}, which clamps it. Adding the two here and clamping there keeps every
     * piece of tower level maths in the one place TDS #45 requires -- this method decides nothing
     * about what a level may be, it only says what to ask for.
     */
    public static Optional<EncounterSnapshot> draw(EncounterPoolDefinition pool, long runSeed, int floorIndex,
                                                   int ordinal, Collection<Integer> partyLevels,
                                                   RulesetDefinition ruleset, int modifierLevelOffset) {
        return draw(pool, runSeed, floorIndex, ordinal, partyLevels, ruleset, modifierLevelOffset, Optional.empty());
    }

    /**
     * The same draw, weighted toward a resolved regional theme's jerseys as the floor deepens (TDS
     * #73). {@code theme} is what {@code pool.regionalPool()} resolves to, if anything -- empty leaves
     * every entry's weight exactly as authored.
     */
    public static Optional<EncounterSnapshot> draw(EncounterPoolDefinition pool, long runSeed, int floorIndex,
                                                   int ordinal, Collection<Integer> partyLevels,
                                                   RulesetDefinition ruleset, int modifierLevelOffset,
                                                   Optional<RegionalThemeDefinition> theme) {
        long seed = EncounterSeed.of(runSeed, floorIndex, ordinal);
        EncounterPoolDefinition.Entry entry = pick(pool, theme, floorIndex, seed);
        int offset = entry.levelOffset() + modifierLevelOffset;
        OptionalInt jerseyNumber = theme.isPresent() && theme.get().isJerseySpecies(entry.species())
                ? OptionalInt.of(JerseyNumbers.forEncounter(seed))
                : OptionalInt.empty();
        return TowerLevelPolicy.levelFor(partyLevels, floorIndex, offset, ruleset).stream()
                .mapToObj(level -> new EncounterSnapshot(ordinal, entry.species(), entry.aspects(), level, jerseyNumber))
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
        return pick(pool, Optional.empty(), 0, seed);
    }

    /** The same walk, with a resolved theme's jersey entries weighted heavier by {@link RegionalWeighting}. */
    static EncounterPoolDefinition.Entry pick(EncounterPoolDefinition pool, Optional<RegionalThemeDefinition> theme,
                                              int floorIndex, long seed) {
        int total = RegionalWeighting.totalWeight(pool, theme, floorIndex);
        // Math.floorMod, not %, because a negative seed would otherwise index backwards off the end.
        int roll = (int) Math.floorMod(seed, total);
        for (EncounterPoolDefinition.Entry entry : pool.entries()) {
            roll -= RegionalWeighting.weightFor(entry, theme, floorIndex);
            if (roll < 0) return entry;
        }
        // Unreachable while weights are positive, which the record enforces; still, a pool is content.
        return pool.entries().get(pool.entries().size() - 1);
    }
}
