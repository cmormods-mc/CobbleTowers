package com.cobbletowers.mastery;

import com.cobbletowers.definition.AchievementDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Which achievements a cycle clear unlocks (P31). Pure: definitions, what the player already has, the clear, and their
 * lifetime figures after counting it.
 */
public final class MasteryEvaluator {

    private MasteryEvaluator() {}

    /** A player's lifetime figures in one tower, as they stand after the clear being judged. */
    public record Lifetime(int cyclesCleared, int ascensionReached) {}

    /** Whether one clear meets every constraint a CLEAR condition sets. */
    public static boolean clearMeets(AchievementDefinition.Condition condition, CycleResult clear) {
        return clear.ascension() >= condition.minAscension()
                && (!condition.flawless() || clear.flawless())
                && (!condition.solo() || clear.startedSolo())
                && (condition.maxSeconds() == 0 || clear.activeMillis() <= condition.maxSeconds() * 1000L)
                && clear.severeModifiers() >= condition.minSevere()
                && clear.score() >= condition.minScore();
    }

    /**
     * The achievements newly unlocked, in a stable order (by id), so a crash and a replay announce them identically. A player
     * keeps what they already hold: nothing is ever taken away or granted twice.
     */
    public static List<AchievementDefinition> unlocked(Collection<AchievementDefinition> definitions,
                                                       Set<ResourceLocation> alreadyHeld, CycleResult clear,
                                                       Lifetime lifetime) {
        List<AchievementDefinition> fresh = new ArrayList<>();
        for (AchievementDefinition definition : definitions) {
            if (alreadyHeld.contains(definition.id())) continue;
            AchievementDefinition.Condition condition = definition.condition();
            boolean met = switch (condition.kind()) {
                case CLEAR -> clearMeets(condition, clear);
                case CYCLES_CLEARED -> lifetime.cyclesCleared() >= condition.threshold();
                case ASCENSION_REACHED -> lifetime.ascensionReached() >= condition.threshold();
            };
            if (met) fresh.add(definition);
        }
        fresh.sort(Comparator.comparing(definition -> definition.id().toString()));
        return List.copyOf(fresh);
    }

    /**
     * As above, for a moment when no cycle was cleared but depth was reached (entering an Ascension): only the lifetime
     * achievements can unlock.
     */
    public static List<AchievementDefinition> unlockedByDepth(Collection<AchievementDefinition> definitions,
                                                              Set<ResourceLocation> alreadyHeld, Lifetime lifetime) {
        List<AchievementDefinition> fresh = new ArrayList<>();
        for (AchievementDefinition definition : definitions) {
            if (alreadyHeld.contains(definition.id())) continue;
            AchievementDefinition.Condition condition = definition.condition();
            boolean met = switch (condition.kind()) {
                case CLEAR -> false;
                case CYCLES_CLEARED -> lifetime.cyclesCleared() >= condition.threshold();
                case ASCENSION_REACHED -> lifetime.ascensionReached() >= condition.threshold();
            };
            if (met) fresh.add(definition);
        }
        fresh.sort(Comparator.comparing(definition -> definition.id().toString()));
        return List.copyOf(fresh);
    }
}
