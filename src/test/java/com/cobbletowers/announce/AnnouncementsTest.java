package com.cobbletowers.announce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.mastery.LeaderboardRules.Board;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnnouncementsTest {

    @Test
    @DisplayName("the first Ascension and every fifth after it are milestones, nothing in between and nothing below one")
    void milestones() {
        for (int level : new int[] {1, 5, 10, 15, 20}) assertTrue(Announcements.isMilestone(level), "level " + level);
        for (int level : new int[] {-5, 0, 2, 3, 4, 6, 11, 19}) assertFalse(Announcements.isMilestone(level), "level " + level);
    }

    @Test
    @DisplayName("only first place on a record board is announced")
    void onlyFirstPlace() {
        assertTrue(Announcements.record(Board.DIFFICULTY, 1, "Battle Tower", List.of("Ash"), 140).isPresent());
        assertTrue(Announcements.record(Board.DIFFICULTY, 2, "Battle Tower", List.of("Ash"), 140).isEmpty());
        assertTrue(Announcements.record(Board.DIFFICULTY, 0, "Battle Tower", List.of("Ash"), 140).isEmpty(), "0 means it did not make the board");
    }

    @Test
    @DisplayName("Clears and trial boards never announce, and neither does an entry with nobody on it")
    void otherBoardsAreQuiet() {
        assertTrue(Announcements.record(Board.CLEARS, 1, "Battle Tower", List.of("Ash"), 9).isEmpty());
        assertTrue(Announcements.record(Board.TRIAL, 1, "daily/2026-10-05", List.of("Ash"), 9).isEmpty());
        assertTrue(Announcements.record(Board.SPEED, 1, "Battle Tower", List.of(), 9).isEmpty());
    }

    @Test
    @DisplayName("each record board says its own number in its own unit")
    void sentences() {
        assertEquals("Ash set a new record: the fastest cycle of Battle Tower (12:05)!",
                Announcements.record(Board.SPEED, 1, "Battle Tower", List.of("Ash"), 725_000).orElseThrow());
        assertEquals("Ash and Misty set a new record: the highest-difficulty clear of Battle Tower (score 140)!",
                Announcements.record(Board.DIFFICULTY, 1, "Battle Tower", List.of("Ash", "Misty"), 140).orElseThrow());
        assertEquals("Ash, Misty and Brock set a new record: the deepest Ascension in Battle Tower (Ascension 7)!",
                Announcements.record(Board.ASCENSION, 1, "Battle Tower", List.of("Ash", "Misty", "Brock"), 7).orElseThrow());
    }

    @Test
    @DisplayName("a milestone Ascension is announced by name, any other level is not")
    void ascensionSentence() {
        assertEquals("Ash reached Ascension 5 in Battle Tower!", Announcements.ascension("Battle Tower", "Ash", 5).orElseThrow());
        assertTrue(Announcements.ascension("Battle Tower", "Ash", 4).isEmpty());
    }

    @Test
    @DisplayName("a duration under a minute and a negative one still read sensibly")
    void durations() {
        assertEquals("0:09", Announcements.duration(9_400));
        assertEquals("0:00", Announcements.duration(-5));
        assertEquals("61:01", Announcements.duration(3_661_000));
    }
}
