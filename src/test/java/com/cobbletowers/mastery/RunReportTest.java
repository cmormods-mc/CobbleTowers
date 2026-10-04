package com.cobbletowers.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunReportTest {

    @Test
    @DisplayName("a clean trial run reads as a card: floors, time, no faints, the trial score and the streak")
    void trialCard() {
        RunReport report = new RunReport("Tideforge Tower", "Daily Trial: Tide Pool Rush", "completed", 5, 0, 754_000, 0, 5,
                List.of("Downpour", "Empty Pockets"), 0, 6120, true, List.of("Spotless"), "Daily streak: 4 days");
        List<String> lines = report.lines();
        assertEquals("Run report: Tideforge Tower (Daily Trial: Tide Pool Rush) - completed", lines.get(0));
        assertEquals("  5 floors cleared, 12:34 fighting", lines.get(1));
        assertEquals("  No Pokemon fainted; best flawless streak 5 floors", lines.get(2));
        assertTrue(lines.contains("  Modifiers: Downpour, Empty Pockets"));
        assertTrue(lines.contains("  Trial score 6120"));
        assertTrue(lines.contains("  Unlocked: Spotless"));
        assertTrue(lines.contains("  Daily streak: 4 days"));
    }

    @Test
    @DisplayName("a wiped run in Ascension reads honestly, and empty parts are left out")
    void wipedCard() {
        RunReport report = new RunReport("Neutral Tower", "", "wiped", 1, 2, 90_000, 3, 1, List.of(), 2, 41, false, List.of(), "");
        List<String> lines = report.lines();
        assertEquals("Run report: Neutral Tower - wiped", lines.get(0));
        assertEquals("  1 floor cleared, Ascension 2, 1:30 fighting", lines.get(1));
        assertEquals("  3 Pokemon fainted; 2 vendor purchases", lines.get(2));
        assertEquals(4, lines.size(), lines.toString());
        assertEquals("  Difficulty score 41", lines.get(3));
    }

    @Test
    @DisplayName("the share line is one line with the team, the result and whether it was flawless")
    void shareLine() {
        RunReport report = new RunReport("Duskvale Tower", "Monotype", "completed", 10, 1, 1_500_000, 0, 10, List.of(), 0, 55, false,
                List.of(), "");
        assertEquals("Ash, Misty: Duskvale Tower (Monotype), 10 floors in 25:00, Ascension 1, flawless, score 55", report.shareLine("Ash, Misty"));
    }
}
