package com.cobbletowers.mastery;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * What one cycle clear amounted to (P31): the unit achievements and leaderboards are judged on, gathered by {@link
 * MasteryService}.
 * @param ascension the cycle's Ascension (0 for base)
 * @param startedSolo the run began with one player
 * @param activeMillis sum of the cycle's floor durations
 * @param flawless no player Pokemon fainted
 * @param severeModifiers severe modifiers held, locked-in counted twice
 * @param score the {@link DifficultyScore}
 * @param rulesetRevision pinned ruleset revision (TDS #90)
 * @param towerRevision pinned tower revision
 * @param towerDigest the tower's digest at start
 */
public record CycleResult(
        UUID runId,
        ResourceLocation tower,
        int ascension,
        List<UUID> players,
        boolean startedSolo,
        long activeMillis,
        boolean flawless,
        int severeModifiers,
        int score,
        int rulesetRevision,
        int towerRevision,
        String towerDigest,
        long at) {

    public CycleResult {
        players = List.copyOf(players);
    }
}
