package com.cobbletowers.report;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The last few things that happened to each run (P48), kept in memory so a player's report can say what the run was
 * doing. Bounded in both directions: {@value #PER_RUN} lines a run, {@value #RUNS} runs, oldest run dropped first. Not
 * saved: a restart forgets it, which is fine for a report written minutes after the trouble.
 */
public final class RunHistory {

    public static final int PER_RUN = 12;
    public static final int RUNS = 200;

    private static final Map<UUID, Deque<String>> HISTORY = new LinkedHashMap<>();

    private RunHistory() {}

    /** Adds a line to a run's history. */
    public static synchronized void note(UUID run, String line) {
        Deque<String> lines = HISTORY.remove(run);
        if (lines == null) lines = new ArrayDeque<>();
        lines.addLast(line);
        while (lines.size() > PER_RUN) lines.removeFirst();
        HISTORY.put(run, lines);
        while (HISTORY.size() > RUNS) {
            UUID oldest = HISTORY.keySet().iterator().next();
            HISTORY.remove(oldest);
        }
    }

    /** A run's lines, oldest first; empty for a run nothing is known about. */
    public static synchronized List<String> recent(UUID run) {
        Deque<String> lines = HISTORY.get(run);
        return lines == null ? List.of() : new ArrayList<>(lines);
    }

    /** Test seam and shutdown. */
    public static synchronized void clear() {
        HISTORY.clear();
    }
}
