package com.cobbletowers.mastery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bits of a run's story gathered until its report is built (P32d): unlocked achievements and the trial score and
 * streak line. In memory, removed when the report is made. Also keeps each player's last report for {@code /tower
 * report}.
 */
public final class RunSummaries {

    private static final Map<UUID, List<String>> UNLOCKED = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> TRIAL_SCORE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> STREAK_LINE = new ConcurrentHashMap<>();
    private static final Map<UUID, RunReport> LAST_REPORT = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_PLAYERS = new ConcurrentHashMap<>();

    private RunSummaries() {}

    public static void unlocked(UUID runId, String achievement) {
        UNLOCKED.computeIfAbsent(runId, id -> new ArrayList<>()).add(achievement);
    }

    public static void trialScore(UUID runId, int score) {
        TRIAL_SCORE.put(runId, score);
    }

    public static void streakLine(UUID runId, String line) {
        STREAK_LINE.merge(runId, line, (a, b) -> a + "; " + b);
    }

    /** Takes (and forgets) what was gathered for a run. */
    public record Gathered(List<String> unlocked, Optional<Integer> trialScore, String streakLine) {}

    public static Gathered take(UUID runId) {
        List<String> unlocked = UNLOCKED.remove(runId);
        return new Gathered(unlocked == null ? List.of() : List.copyOf(unlocked), Optional.ofNullable(TRIAL_SCORE.remove(runId)),
                STREAK_LINE.getOrDefault(runId, ""));
    }

    public static void forgetStreakLine(UUID runId) {
        STREAK_LINE.remove(runId);
    }

    public static void remember(UUID player, RunReport report, String players) {
        LAST_REPORT.put(player, report);
        LAST_PLAYERS.put(player, players);
    }

    public static Optional<RunReport> lastReportOf(UUID player) {
        return Optional.ofNullable(LAST_REPORT.get(player));
    }

    public static String lastPlayersOf(UUID player) {
        return LAST_PLAYERS.getOrDefault(player, "");
    }

    /** For a server stop. */
    public static void clear() {
        UNLOCKED.clear();
        TRIAL_SCORE.clear();
        STREAK_LINE.clear();
        LAST_REPORT.clear();
        LAST_PLAYERS.clear();
    }
}
