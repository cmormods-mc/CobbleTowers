package com.cobbletowers.mastery;

import com.cobbletowers.ServerState;
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

    /** What has been gathered for one run so far. */
    private static final class Story {
        final List<String> unlocked = new ArrayList<>();
        Integer trialScore;
        String streakLine = "";
    }

    private record Last(RunReport report, String players) {}

    private static final Map<UUID, Story> BY_RUN = new ConcurrentHashMap<>();
    private static final Map<UUID, Last> LAST_BY_PLAYER = new ConcurrentHashMap<>();

    static {
        ServerState.onStop(() -> {
            BY_RUN.clear();
            LAST_BY_PLAYER.clear();
        });
    }

    private RunSummaries() {}

    private static Story story(UUID runId) {
        return BY_RUN.computeIfAbsent(runId, id -> new Story());
    }

    public static void unlocked(UUID runId, String achievement) {
        Story story = story(runId);
        synchronized (story) {
            story.unlocked.add(achievement);
        }
    }

    public static void trialScore(UUID runId, int score) {
        story(runId).trialScore = score;
    }

    public static void streakLine(UUID runId, String line) {
        Story story = story(runId);
        synchronized (story) {
            story.streakLine = story.streakLine.isEmpty() ? line : story.streakLine + "; " + line;
        }
    }

    /** Takes (and forgets) what was gathered for a run. */
    public record Gathered(List<String> unlocked, Optional<Integer> trialScore, String streakLine) {}

    public static Gathered take(UUID runId) {
        Story story = BY_RUN.remove(runId);
        if (story == null) return new Gathered(List.of(), Optional.empty(), "");
        synchronized (story) {
            return new Gathered(List.copyOf(story.unlocked), Optional.ofNullable(story.trialScore), story.streakLine);
        }
    }

    /** Drops anything gathered for a run whose report was not built. */
    public static void discard(UUID runId) {
        BY_RUN.remove(runId);
    }

    public static void remember(UUID player, RunReport report, String players) {
        LAST_BY_PLAYER.put(player, new Last(report, players));
    }

    public static Optional<RunReport> lastReportOf(UUID player) {
        return Optional.ofNullable(LAST_BY_PLAYER.get(player)).map(Last::report);
    }

    public static String lastPlayersOf(UUID player) {
        Last last = LAST_BY_PLAYER.get(player);
        return last == null ? "" : last.players();
    }
}
