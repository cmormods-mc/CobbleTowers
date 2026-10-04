package com.cobbletowers.mastery;

import java.util.ArrayList;
import java.util.List;

/**
 * The card shown at the end of a run (P32d), built from plain values so it can be read, tested and later drawn on a screen without
 * a server. {@link #lines} is the chat version; {@link #shareLine} is the single line a player can paste.
 *
 * @param tower          the tower's name
 * @param mode           the playlist or trial in words, empty for an ordinary run
 * @param outcome        how it ended: {@code completed}, {@code cashed out}, {@code wiped}, {@code abandoned}
 * @param floorsCleared  floors cleared in the run
 * @param ascension      the Ascension reached (0 for the base cycle)
 * @param activeMillis   time spent fighting
 * @param faints         player Pokemon that fainted
 * @param bestFlawlessRun the most floors cleared in a row with no faint
 * @param modifiers      the modifiers the run held, by name
 * @param purchases      vendor purchases made
 * @param score          the difficulty score, or the trial score when {@code trial} is set
 * @param trial          whether {@code score} is a trial score
 * @param achievements   achievements unlocked during the run, by name
 * @param streakLine     the daily streak line, empty if none
 */
public record RunReport(String tower, String mode, String outcome, int floorsCleared, int ascension, long activeMillis, int faints,
                        int bestFlawlessRun, List<String> modifiers, int purchases, int score, boolean trial,
                        List<String> achievements, String streakLine) {

    public RunReport {
        modifiers = List.copyOf(modifiers);
        achievements = List.copyOf(achievements);
    }

    /** The card, one line each. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("Run report: " + tower + (mode.isEmpty() ? "" : " (" + mode + ")") + " - " + outcome);
        lines.add("  " + floorsCleared + " floor" + (floorsCleared == 1 ? "" : "s") + " cleared"
                + (ascension > 0 ? ", Ascension " + ascension : "") + ", " + MasteryView.duration(activeMillis) + " fighting");
        lines.add("  " + (faints == 0 ? "No Pokemon fainted" : faints + " Pokemon fainted")
                + (bestFlawlessRun > 1 ? "; best flawless streak " + bestFlawlessRun + " floors" : "")
                + (purchases > 0 ? "; " + purchases + " vendor purchase" + (purchases == 1 ? "" : "s") : ""));
        if (!modifiers.isEmpty()) lines.add("  Modifiers: " + String.join(", ", modifiers));
        lines.add("  " + (trial ? "Trial score " : "Difficulty score ") + score);
        if (!achievements.isEmpty()) lines.add("  Unlocked: " + String.join(", ", achievements));
        if (!streakLine.isEmpty()) lines.add("  " + streakLine);
        return List.copyOf(lines);
    }

    /** One line to paste in chat. */
    public String shareLine(String players) {
        return players + ": " + tower + (mode.isEmpty() ? "" : " (" + mode + ")") + ", " + floorsCleared + " floors in "
                + MasteryView.duration(activeMillis) + (ascension > 0 ? ", Ascension " + ascension : "")
                + (faints == 0 ? ", flawless" : "") + ", " + (trial ? "trial score " : "score ") + score;
    }
}
