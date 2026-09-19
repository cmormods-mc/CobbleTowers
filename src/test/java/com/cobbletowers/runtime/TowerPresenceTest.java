package com.cobbletowers.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The watchdog's verdict, decided from a clock rather than from a sleep.
 *
 * <p>This is the whole of TDS #59's rule. A test that had to wait ten minutes to see it would never
 * be run, which is why the judgement was written as a function of readings in the first place.
 */
class TowerPresenceTest {

    private static final long NOW = 1_726_000_000_000L;
    private static final UUID ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static Map<UUID, Long> activity(long one, long two) {
        Map<UUID, Long> readings = new LinkedHashMap<>();
        readings.put(ONE, one);
        readings.put(TWO, two);
        return readings;
    }

    @Test
    @DisplayName("a floor everybody is playing is left alone")
    void quietFloor() {
        TowerPresence.Verdict verdict = TowerPresence.judge(activity(NOW - 5_000, NOW - 30_000), NOW - 60_000, NOW);

        assertTrue(verdict.isQuiet());
        assertEquals(List.of(), verdict.drop());
        assertFalse(verdict.park());
    }

    @Test
    @DisplayName("one player who has stopped answering is dropped, and the rest of the floor is not")
    void oneStalledPlayer() {
        // The case P5 hit for real: a lead with no legal move answered "Invalid action choice"
        // for ever, and the floor stayed open holding the round, the cell and its chunk tickets.
        long stalled = NOW - TowerPresence.PLAYER_STALL_MILLIS - 1;

        TowerPresence.Verdict verdict = TowerPresence.judge(activity(stalled, NOW - 1_000), NOW - 60_000, NOW);

        assertEquals(List.of(ONE), verdict.drop());
        assertFalse(verdict.park(), "the floor is still moving for somebody");
    }

    @Test
    @DisplayName("the cap is a boundary, not an approximation")
    void exactlyAtTheCap() {
        TowerPresence.Verdict at = TowerPresence.judge(
                activity(NOW - TowerPresence.PLAYER_STALL_MILLIS, NOW), NOW - 60_000, NOW);
        assertTrue(at.isQuiet(), "exactly at the cap has not passed it");

        TowerPresence.Verdict past = TowerPresence.judge(
                activity(NOW - TowerPresence.PLAYER_STALL_MILLIS - 1, NOW), NOW - 60_000, NOW);
        assertEquals(List.of(ONE), past.drop());
    }

    @Test
    @DisplayName("a floor where nothing at all has happened parks the run instead of dropping everybody")
    void stalledFloorParksRatherThanWipes() {
        // The distinction that matters: dropping everybody would settle the floor as a wipe, and a
        // wipe forfeits the unclaimed pool. An engine that stopped is not a party that lost.
        long ancient = NOW - TowerPresence.FLOOR_STALL_MILLIS - 1;

        TowerPresence.Verdict verdict = TowerPresence.judge(activity(ancient, ancient), ancient, NOW);

        assertTrue(verdict.park());
        assertEquals(List.of(), verdict.drop(), "nobody is dropped when the whole floor is the problem");
        assertFalse(verdict.isQuiet());
    }

    @Test
    @DisplayName("one player moving recently keeps the whole floor off the park verdict")
    void oneActivePlayerKeepsTheFloorAlive() {
        long ancient = NOW - TowerPresence.FLOOR_STALL_MILLIS - 1;

        TowerPresence.Verdict verdict = TowerPresence.judge(activity(ancient, NOW - 1_000), ancient, NOW);

        assertFalse(verdict.park());
        assertEquals(List.of(ONE), verdict.drop());
    }

    @Test
    @DisplayName("a floor with no battles left open is judged by when the floor itself began")
    void noBattlesFallsBackToTheRoundStart() {
        // Every battle has ended and the floor has not settled: nothing would be measured otherwise,
        // and an empty reading set would read as "no activity is overdue" for ever.
        assertTrue(TowerPresence.judge(Map.of(), NOW - 1_000, NOW).isQuiet());
        assertTrue(TowerPresence.judge(Map.of(), NOW - TowerPresence.FLOOR_STALL_MILLIS - 1, NOW).park());
    }

    @Test
    @DisplayName("a player's cap is well short of the floor's, or one stalled player would park the run")
    void theTwoCapsAreOrdered() {
        assertTrue(TowerPresence.PLAYER_STALL_MILLIS < TowerPresence.FLOOR_STALL_MILLIS,
                "the two verdicts are only distinguishable while the caps are");
    }
}
