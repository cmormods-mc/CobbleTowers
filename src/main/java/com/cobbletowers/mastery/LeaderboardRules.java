package com.cobbletowers.mastery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * How a leaderboard is ordered, capped and updated (P31), with no server in sight.
 *
 * <p>An entry records everything needed to compare it fairly later: the ruleset revision, tower revision and digest the run
 * was played against (TDS #90), the Ascension and difficulty score of the achievement, and who was on the team.
 */
public final class LeaderboardRules {

    /** The four boards. The Clears board is per individual; the others are per run. */
    public enum Board {
        ASCENSION("Deepest Ascension", false, true),
        SPEED("Fastest cycle", true, true),
        DIFFICULTY("Highest difficulty", false, true),
        CLEARS("Most cycles cleared", false, false),
        /** One trial's results (P32): the key's tower is the trial's board id, such as {@code daily/2026-10-05}. */
        TRIAL("Trial", false, true);

        private final String title;
        private final boolean lowerIsBetter;
        private final boolean hasMode;

        Board(String title, boolean lowerIsBetter, boolean hasMode) {
            this.title = title;
            this.lowerIsBetter = lowerIsBetter;
            this.hasMode = hasMode;
        }

        public String title() { return title; }

        public boolean lowerIsBetter() { return lowerIsBetter; }

        /** Whether solo and team runs are ranked apart. */
        public boolean hasMode() { return hasMode; }
    }

    /** Solo is a run that started with one player. */
    public enum Mode { SOLO, TEAM, ANY }

    /** How many entries a board keeps. */
    public static final int CAPACITY = 50;

    /**
     * One ranked result.
     *
     * @param players     who earned it, as (id, name at the time) pairs
     * @param value       depth, milliseconds, score or count, whichever the board ranks
     * @param runId       the run, or null for the Clears board (which has no run)
     */
    public record Entry(List<Member> players, long value, UUID runId, int ascension, int score, int rulesetRevision,
                        int towerRevision, String towerDigest, long at) {
        public Entry {
            players = List.copyOf(players);
        }
    }

    public record Member(UUID id, String name) {}

    private LeaderboardRules() {}

    /** Whether {@code candidate} ranks ahead of {@code other}: a better value, or the same value reached earlier. */
    public static boolean beats(Board board, Entry candidate, Entry other) {
        if (candidate.value() != other.value()) {
            return board.lowerIsBetter() ? candidate.value() < other.value() : candidate.value() > other.value();
        }
        return candidate.at() < other.at();
    }

    /**
     * The board after offering it {@code entry}: sorted best first, capped, and at most one entry per run (an entry for a run
     * already on the board replaces it only if it is better, so a run cannot be pushed down by its own later, worse result).
     * The Clears board has no run and keys an entry on its single player instead.
     */
    public static List<Entry> offer(Board board, List<Entry> current, Entry entry) {
        List<Entry> next = new ArrayList<>(current);
        for (int i = 0; i < next.size(); i++) {
            Entry existing = next.get(i);
            if (sameSubject(existing, entry)) {
                // Only a strictly better value replaces it; the same value keeps the earlier entry.
                boolean better = board.lowerIsBetter() ? entry.value() < existing.value() : entry.value() > existing.value();
                if (!better) return List.copyOf(current);
                next.remove(i);
                break;
            }
        }
        next.add(entry);
        next.sort(comparator(board));
        if (next.size() > CAPACITY) next = new ArrayList<>(next.subList(0, CAPACITY));
        return List.copyOf(next);
    }

    /** One entry per run; for entries with no run, per (sole) player. */
    private static boolean sameSubject(Entry a, Entry b) {
        if (a.runId() != null || b.runId() != null) return a.runId() != null && a.runId().equals(b.runId());
        return a.players().size() == 1 && b.players().size() == 1 && a.players().get(0).id().equals(b.players().get(0).id());
    }

    private static Comparator<Entry> comparator(Board board) {
        Comparator<Entry> byValue = Comparator.comparingLong(Entry::value);
        if (!board.lowerIsBetter()) byValue = byValue.reversed();
        return byValue.thenComparingLong(Entry::at);
    }

    /**
     * A board's identity in storage: board, tower, mode (ANY for a board that has no mode), playlist ({@code ""} for
     * Standard), so a Monotype clear is ranked apart from a Standard one (P32), and season ({@code ""} for the all-time
     * board, otherwise {@code s<number>}, P36a).
     */
    public record Key(Board board, ResourceLocation tower, Mode mode, String playlist, String season) {
        public Key {
            if (!board.hasMode()) mode = Mode.ANY;
            playlist = playlist == null ? "" : playlist;
            season = season == null ? "" : season;
        }

        /** An all-time board of a playlist (every key before P36a). */
        public Key(Board board, ResourceLocation tower, Mode mode, String playlist) {
            this(board, tower, mode, playlist, "");
        }

        /** A Standard all-time board. */
        public Key(Board board, ResourceLocation tower, Mode mode) {
            this(board, tower, mode, "", "");
        }

        /** The same board in a season ({@code ""} for all-time). */
        public Key inSeason(String seasonId) {
            return new Key(board, tower, mode, playlist, seasonId);
        }

        public boolean allTime() {
            return season.isEmpty();
        }
    }

    /** The mode a run is ranked in. */
    public static Mode modeOf(boolean startedSolo) {
        return startedSolo ? Mode.SOLO : Mode.TEAM;
    }
}
