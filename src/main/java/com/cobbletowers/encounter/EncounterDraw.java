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
 * Which opponents a floor puts up. Deterministic from {@link EncounterSeed} (TDS #29), so a resumed run cannot
 * reroll; pure, so tested without Minecraft.
 */
public final class EncounterDraw {

    private EncounterDraw() {}

    /** One opponent for one player on one floor. @param ordinal which opponent of the floor this is */
    public static Optional<EncounterSnapshot> draw(EncounterPoolDefinition pool, long runSeed, int floorIndex,
                                                   int ordinal, Collection<Integer> partyLevels,
                                                   RulesetDefinition ruleset) {
        return draw(pool, runSeed, floorIndex, ordinal, partyLevels, ruleset, 0);
    }

    /**
     * The same draw with a run's modifiers folded in; the level offset is added here and clamped by {@link
     * TowerLevelPolicy} (TDS #45).
     */
    public static Optional<EncounterSnapshot> draw(EncounterPoolDefinition pool, long runSeed, int floorIndex,
                                                   int ordinal, Collection<Integer> partyLevels,
                                                   RulesetDefinition ruleset, int modifierLevelOffset) {
        return draw(pool, runSeed, floorIndex, ordinal, partyLevels, ruleset, modifierLevelOffset, Optional.empty());
    }

    /**
     * The same draw, weighted toward a regional theme's jerseys as the floor deepens (TDS #73). An empty theme leaves
     * weights as authored.
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
        List<String> aspects = jerseyNumber.isPresent()
                ? jerseyProperties(entry.aspects(), theme.get(), jerseyNumber.getAsInt())
                : entry.aspects();
        return TowerLevelPolicy.levelFor(partyLevels, floorIndex, offset, ruleset).stream()
                .mapToObj(level -> new EncounterSnapshot(ordinal, entry.species(), aspects, level, jerseyNumber))
                .findFirst();
    }

    /**
     * Battle Tower Teams pack features: {@code league_team=<region>} and a two-digit {@code jersey_number}. Ignored
     * without the pack; the battle adapter retries without aspects.
     */
    static List<String> jerseyProperties(List<String> authored, RegionalThemeDefinition theme, int number) {
        List<String> aspects = new ArrayList<>(authored);
        aspects.remove("jersey");
        aspects.add("league_team=" + theme.id().getPath());
        aspects.add(String.format("jersey_number=%02d", number));
        return aspects;
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
     * The weighted pick: walks entries subtracting weights in declared order. Editing a pool shifts every seed's
     * draw; runs pin the content digest (TDS #40) to make that visible.
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
