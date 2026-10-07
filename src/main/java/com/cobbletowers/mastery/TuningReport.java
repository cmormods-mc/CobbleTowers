package com.cobbletowers.mastery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * What collected data says about the guessed numbers (P31, P32): who holds each achievement, where mastery levels and
 * Ascension depths sit, how trial attempts and scores spread and how far apart a board's entries are. Calls out the
 * cases that most want a second look. Changes nothing; pure.
 */
public final class TuningReport {

    /** A player's standing in one tower. */
    public record Standing(UUID player, ResourceLocation tower, int cycles, int ascension, Set<ResourceLocation> unlocked) {
        public Standing {
            unlocked = Set.copyOf(unlocked);
        }
    }

    /** An achievement as the report needs it. */
    public record Achievement(ResourceLocation id, String name) {}

    /** One player's attempt at one trial. */
    public record TrialAttempt(String instanceId, boolean finished, int score, int floorsCleared) {}

    /** One board's values (times, scores) with its label. */
    public record Board(String label, boolean lowerIsBetter, List<Long> values) {
        public Board {
            values = List.copyOf(values);
        }
    }

    /** Enough players for a rate to mean anything; below this the report says so instead of drawing conclusions. */
    public static final int MIN_PLAYERS = 5;
    /** An achievement held by this share of players or more is probably too easy; by none, perhaps unreachable. */
    public static final double TOO_EASY = 0.90;

    private TuningReport() {}

    public static List<String> build(List<Standing> standings, List<Achievement> achievements, List<TrialAttempt> attempts,
                                     List<Board> boards) {
        List<String> lines = new ArrayList<>();
        Set<UUID> players = new java.util.HashSet<>();
        for (Standing standing : standings) players.add(standing.player());
        lines.add("Tuning report: " + players.size() + " player(s) with tower records, " + attempts.size() + " trial attempt(s), "
                + boards.stream().mapToInt(board -> board.values().size()).sum() + " board entr(ies).");
        if (players.size() < MIN_PLAYERS) {
            lines.add("  Fewer than " + MIN_PLAYERS + " players: the rates below are anecdotes, not evidence. Treat them as a sanity check only.");
        }

        // ---- mastery, per tower ---------------------------------------------------------------------------------
        Map<ResourceLocation, List<Standing>> byTower = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        for (Standing standing : standings) byTower.computeIfAbsent(standing.tower(), id -> new ArrayList<>()).add(standing);
        for (Map.Entry<ResourceLocation, List<Standing>> tower : byTower.entrySet()) {
            List<Standing> here = tower.getValue();
            lines.add("");
            lines.add(tower.getKey() + ": " + here.size() + " player(s)");
            lines.add("  mastery level: " + spread(here.stream().map(s -> (long) s.unlocked().size()).toList()));
            lines.add("  cycles cleared: " + spread(here.stream().map(s -> (long) s.cycles()).toList()));
            lines.add("  deepest Ascension: " + histogram(here.stream().map(Standing::ascension).toList()));
            List<String> flags = new ArrayList<>();
            List<String> rows = new ArrayList<>();
            for (Achievement achievement : achievements) {
                long held = here.stream().filter(s -> s.unlocked().contains(achievement.id())).count();
                double rate = here.isEmpty() ? 0 : (double) held / here.size();
                rows.add(String.format("    %3d%%  %s (%d)", Math.round(rate * 100), achievement.name(), held));
                if (here.size() >= MIN_PLAYERS && held == 0) flags.add("nobody holds " + achievement.name() + ": too hard, or unreachable?");
                if (here.size() >= MIN_PLAYERS && rate >= TOO_EASY) flags.add(Math.round(rate * 100) + "% hold " + achievement.name() + ": too easy?");
            }
            lines.add("  achievements, most held first:");
            rows.sort(Comparator.comparing((String row) -> Integer.parseInt(row.substring(4, 7).trim())).reversed());
            lines.addAll(rows);
            for (String flag : flags) lines.add("  ! " + flag);
        }

        // ---- trials ----------------------------------------------------------------------------------------------
        Map<String, List<TrialAttempt>> byInstance = new LinkedHashMap<>();
        for (TrialAttempt attempt : attempts) byInstance.computeIfAbsent(attempt.instanceId(), id -> new ArrayList<>()).add(attempt);
        if (!byInstance.isEmpty()) {
            lines.add("");
            lines.add("Trials:");
            for (Map.Entry<String, List<TrialAttempt>> trial : byInstance.entrySet()) {
                List<TrialAttempt> all = trial.getValue();
                List<TrialAttempt> done = all.stream().filter(TrialAttempt::finished).toList();
                lines.add("  " + trial.getKey() + ": " + all.size() + " attempt(s), " + done.size() + " finished; floors cleared "
                        + spread(done.stream().map(a -> (long) a.floorsCleared()).toList())
                        + "; score " + spread(done.stream().map(a -> (long) a.score()).toList()));
            }
        }

        // ---- boards ----------------------------------------------------------------------------------------------
        List<String> boardLines = new ArrayList<>();
        for (Board board : boards) {
            if (board.values().isEmpty()) continue;
            boardLines.add("  " + board.label() + ": " + spread(board.values()) + (board.lowerIsBetter() ? " (lower is better)" : ""));
        }
        if (!boardLines.isEmpty()) {
            lines.add("");
            lines.add("Boards (min / median / max):");
            lines.addAll(boardLines);
        }
        return lines;
    }

    /** {@code min / median / max (n)} of the values, or {@code none}. */
    static String spread(Collection<Long> values) {
        if (values.isEmpty()) return "none";
        List<Long> sorted = values.stream().sorted().toList();
        long median = sorted.get(sorted.size() / 2);
        return sorted.get(0) + " / " + median + " / " + sorted.get(sorted.size() - 1) + " (n=" + sorted.size() + ")";
    }

    /** {@code value x count} pairs in order, e.g. {@code 0 x 4, 1 x 2, 3 x 1}. */
    static String histogram(Collection<Integer> values) {
        if (values.isEmpty()) return "none";
        Map<Integer, Integer> counts = new TreeMap<>();
        for (int value : values) counts.merge(value, 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        counts.forEach((value, count) -> parts.add(value + " x " + count));
        return String.join(", ", parts);
    }
}
