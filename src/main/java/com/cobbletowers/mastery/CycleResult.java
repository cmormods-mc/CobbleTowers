package com.cobbletowers.mastery;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * What one cycle clear amounted to (P31): the unit mastery achievements and leaderboards are judged on. Everything here is a
 * plain value, gathered by {@link MasteryService} when a run clears the last floor of a cycle, so the rules that read it are
 * pure.
 *
 * @param ascension       the Ascension of the cycle just cleared (0 for the base cycle)
 * @param startedSolo     whether the run began with a single player
 * @param activeMillis    the sum of the cycle's floor durations; time spent between floors does not count
 * @param flawless        no player Pokemon fainted during the cycle
 * @param severeModifiers how many severe modifiers the run held, locked-in ones counted twice
 * @param score           the difficulty score ({@link DifficultyScore})
 * @param rulesetRevision the ruleset revision the run pinned (TDS #90)
 * @param towerRevision   the tower revision the run pinned
 * @param towerDigest     the tower's content digest when the run started
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
