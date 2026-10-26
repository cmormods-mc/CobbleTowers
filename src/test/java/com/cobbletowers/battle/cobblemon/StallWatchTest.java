package com.cobbletowers.battle.cobblemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StallWatchTest {

    private final StallWatch watch = new StallWatch();
    private final UUID battle = UUID.randomUUID();

    @Test
    @DisplayName("a battle idle for a moment is left alone; idle for a few seconds on one turn is a stall")
    void stallAfterIdleMillis() {
        assertFalse(watch.observe(battle, 1, true, 0));
        assertFalse(watch.observe(battle, 1, true, 1_000));
        assertFalse(watch.observe(battle, 1, true, StallWatch.IDLE_MILLIS - 1));
        assertTrue(watch.observe(battle, 1, true, StallWatch.IDLE_MILLIS));
    }

    @Test
    @DisplayName("anyone with something to do resets the clock")
    void activityResets() {
        long idle = StallWatch.IDLE_MILLIS;
        watch.observe(battle, 1, true, 0);
        watch.observe(battle, 1, true, idle - 500);
        assertFalse(watch.observe(battle, 1, false, idle - 100));
        assertFalse(watch.observe(battle, 1, true, idle), "the clock starts again");
        assertFalse(watch.observe(battle, 1, true, 2 * idle - 1));
        assertTrue(watch.observe(battle, 1, true, 2 * idle));
    }

    @Test
    @DisplayName("a new turn starts the clock again, on any turn")
    void newTurnResets() {
        long idle = StallWatch.IDLE_MILLIS;
        watch.observe(battle, 3, true, 0);
        assertFalse(watch.observe(battle, 4, true, 10_000));
        assertFalse(watch.observe(battle, 4, true, 10_000 + idle - 1));
        assertTrue(watch.observe(battle, 4, true, 10_000 + idle), "a stall on turn 4 is caught");
    }

    @Test
    @DisplayName("it nudges again if the first did not help, a few times, then stops")
    void repeatsThenGivesUp() {
        watch.observe(battle, 1, true, 0);
        int nudges = 0;
        for (long t = 500; t <= 120_000; t += 500) if (watch.observe(battle, 1, true, t)) nudges++;
        assertEquals(StallWatch.MAX_NUDGES, nudges);
    }

    @Test
    @DisplayName("repeat nudges are spaced out")
    void spaced() {
        long idle = StallWatch.IDLE_MILLIS;
        watch.observe(battle, 1, true, 0);
        assertTrue(watch.observe(battle, 1, true, idle));
        assertFalse(watch.observe(battle, 1, true, idle + 500));
        assertFalse(watch.observe(battle, 1, true, idle + StallWatch.REPEAT_MILLIS - 1));
        assertTrue(watch.observe(battle, 1, true, idle + StallWatch.REPEAT_MILLIS));
    }

    @Test
    @DisplayName("battles are watched apart, and forgetting one leaves nothing behind")
    void apart() {
        UUID other = UUID.randomUUID();
        watch.observe(battle, 1, true, 0);
        watch.observe(other, 1, true, 1_000);
        assertTrue(watch.observe(battle, 1, true, StallWatch.IDLE_MILLIS));
        assertFalse(watch.observe(other, 1, true, StallWatch.IDLE_MILLIS));
        watch.forget(battle);
        assertEquals(1, watch.size());
        watch.clear();
        assertEquals(0, watch.size());
    }
}
