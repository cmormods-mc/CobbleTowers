package com.cobbletowers.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.mastery.TuningReport.Achievement;
import com.cobbletowers.mastery.TuningReport.Board;
import com.cobbletowers.mastery.TuningReport.Standing;
import com.cobbletowers.mastery.TuningReport.TrialAttempt;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The tuning report (P33): what the collected numbers say about the guessed thresholds. */
class TuningReportTest {

    private static final ResourceLocation TOWER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral");
    private static final ResourceLocation EASY = ResourceLocation.fromNamespaceAndPath("cobbletowers", "easy");
    private static final ResourceLocation HARD = ResourceLocation.fromNamespaceAndPath("cobbletowers", "hard");
    private static final ResourceLocation MID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "mid");
    private static final List<Achievement> ACHIEVEMENTS = List.of(new Achievement(EASY, "Easy One"), new Achievement(HARD, "Hard One"),
            new Achievement(MID, "Middling"));

    private static Standing player(int n, int cycles, int ascension, ResourceLocation... held) {
        return new Standing(new UUID(0, n), TOWER, cycles, ascension, Set.of(held));
    }

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }

    @Test
    @DisplayName("with too few players the report says its rates are anecdotes and flags nothing")
    void fewPlayers() {
        List<String> lines = TuningReport.build(List.of(player(1, 1, 0, EASY), player(2, 0, 0)), ACHIEVEMENTS, List.of(), List.of());
        assertTrue(joined(lines).contains("anecdotes"));
        assertFalse(joined(lines).contains("!"), "no flags on a handful of players");
    }

    @Test
    @DisplayName("with enough players it flags an achievement nobody holds and one nearly everybody does")
    void flags() {
        List<Standing> standings = List.of(player(1, 3, 2, EASY, MID), player(2, 2, 1, EASY, MID), player(3, 5, 4, EASY),
                player(4, 1, 0, EASY), player(5, 0, 0, EASY), player(6, 1, 0, EASY), player(7, 2, 1, EASY, MID),
                player(8, 1, 0, EASY), player(9, 4, 3, EASY), player(10, 1, 0, EASY));
        String report = joined(TuningReport.build(standings, ACHIEVEMENTS, List.of(), List.of()));
        assertTrue(report.contains("! nobody holds Hard One"), report);
        assertTrue(report.contains("! 100% hold Easy One: too easy?"), report);
        assertFalse(report.contains("! 30% hold Middling"), "a middling rate is not flagged");
        assertFalse(report.contains("anecdotes"));
    }

    @Test
    @DisplayName("achievements are listed most held first, with their share and count")
    void ordering() {
        List<Standing> standings = List.of(player(1, 1, 0, MID), player(2, 1, 0, MID), player(3, 1, 0, MID, EASY), player(4, 1, 0),
                player(5, 1, 0));
        List<String> lines = TuningReport.build(standings, ACHIEVEMENTS, List.of(), List.of());
        int mid = lines.indexOf("     60%  Middling (3)");
        int easy = lines.indexOf("     20%  Easy One (1)");
        int hard = lines.indexOf("      0%  Hard One (0)");
        assertTrue(mid >= 0 && easy > mid && hard > easy, joined(lines));
    }

    @Test
    @DisplayName("mastery levels, cycles and Ascension depth are summarised as spreads and a histogram")
    void spreads() {
        List<Standing> standings = List.of(player(1, 1, 0), player(2, 3, 2, EASY), player(3, 5, 2, EASY, MID));
        String report = joined(TuningReport.build(standings, ACHIEVEMENTS, List.of(), List.of()));
        assertTrue(report.contains("mastery level: 0 / 1 / 2 (n=3)"), report);
        assertTrue(report.contains("cycles cleared: 1 / 3 / 5 (n=3)"), report);
        assertTrue(report.contains("deepest Ascension: 0 x 1, 2 x 2"), report);
    }

    @Test
    @DisplayName("a trial's attempts, how many finished, and the spread of floors and score")
    void trials() {
        List<TrialAttempt> attempts = List.of(new TrialAttempt("daily:7", true, 800, 5), new TrialAttempt("daily:7", true, 300, 3),
                new TrialAttempt("daily:7", false, 0, 0), new TrialAttempt("weekly:1", true, 1200, 10));
        String report = joined(TuningReport.build(List.of(), ACHIEVEMENTS, attempts, List.of()));
        assertTrue(report.contains("daily:7: 3 attempt(s), 2 finished; floors cleared 3 / 5 / 5 (n=2); score 300 / 800 / 800 (n=2)"), report);
        assertTrue(report.contains("weekly:1: 1 attempt(s), 1 finished"), report);
    }

    @Test
    @DisplayName("boards show min, median and max, and an empty board is left out")
    void boards() {
        List<Board> boards = List.of(new Board("Speed / neutral / SOLO", true, List.of(400_000L, 600_000L, 900_000L)),
                new Board("Clears / neutral / SOLO", false, List.of()));
        String report = joined(TuningReport.build(List.of(), ACHIEVEMENTS, List.of(), boards));
        assertTrue(report.contains("Speed / neutral / SOLO: 400000 / 600000 / 900000 (n=3) (lower is better)"), report);
        assertFalse(report.contains("Clears"), report);
    }

    @Test
    @DisplayName("nothing at all still makes a report that says so")
    void empty() {
        List<String> lines = TuningReport.build(List.of(), List.of(), List.of(), List.of());
        assertEquals("Tuning report: 0 player(s) with tower records, 0 trial attempt(s), 0 board entr(ies).", lines.get(0));
    }
}
