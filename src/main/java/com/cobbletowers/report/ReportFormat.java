package com.cobbletowers.report;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** The words of a player report (P48): what is cleaned out of the text, and how one report reads in the log. Pure. */
public final class ReportFormat {

    public static final int MAX_TEXT = 400;

    private ReportFormat() {}

    /** One run's facts for a report. */
    public record RunFacts(String runId, String tower, int floor, String state, String trial, List<String> players, long seed,
                           List<String> recent) {}

    /** The text as a single bounded line: control characters and line breaks become spaces, ends trimmed, cut at {@link #MAX_TEXT}. */
    public static String clean(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < MAX_TEXT; i++) {
            char c = raw.charAt(i);
            out.append(Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString().replaceAll(" {2,}", " ").trim();
    }

    /** A short id for a report, from when it was made. */
    public static String idOf(long epochMillis) {
        return "R" + Long.toString(epochMillis / 1000, 36).toUpperCase();
    }

    /** One report as it is written: a header line, the text, the run if the player is in one, then blank line. */
    public static String format(long epochMillis, String reportId, String name, String uuid, String version, String text,
                                Optional<RunFacts> run) {
        StringBuilder out = new StringBuilder();
        out.append('[').append(Instant.ofEpochMilli(epochMillis)).append("] ").append(reportId).append(' ').append(name)
                .append(" (").append(uuid).append(") v").append(version).append('\n');
        out.append("  text: ").append(text.isEmpty() ? "(no text)" : text).append('\n');
        if (run.isEmpty()) {
            out.append("  run: none\n");
        } else {
            RunFacts r = run.get();
            out.append("  run: ").append(r.runId()).append(" tower ").append(r.tower()).append(" floor ").append(r.floor())
                    .append(' ').append(r.state()).append(" trial ").append(r.trial().isEmpty() ? "-" : r.trial())
                    .append(" seed ").append(r.seed()).append(" players ").append(String.join(",", r.players())).append('\n');
            for (String line : r.recent()) out.append("    ").append(line).append('\n');
        }
        return out.append('\n').toString();
    }
}
