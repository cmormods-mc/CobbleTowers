package com.cobbletowers.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HeavyWorkTest {

    @BeforeEach
    void clean() {
        HeavyWork.reset();
    }

    @Test
    @DisplayName("one heavy operation is admitted per window; the next waits until the window has passed")
    void oneAtATime() {
        long t = 10_000;
        assertTrue(HeavyWork.tryAcquire(t));
        assertFalse(HeavyWork.tryAcquire(t + 1), "a second request in the same window is refused");
        assertFalse(HeavyWork.free(t + HeavyWork.WINDOW_MILLIS - 1));
        assertTrue(HeavyWork.free(t + HeavyWork.WINDOW_MILLIS));
        assertTrue(HeavyWork.tryAcquire(t + HeavyWork.WINDOW_MILLIS));
        assertEquals(1, HeavyWork.refusedCount());
    }

    @Test
    @DisplayName("work that could not wait is noted, so the next request backs off around it")
    void notedWorkCounts() {
        HeavyWork.note(5_000);
        assertFalse(HeavyWork.tryAcquire(5_100));
        assertTrue(HeavyWork.tryAcquire(5_000 + HeavyWork.WINDOW_MILLIS));
    }

    @Test
    @DisplayName("a clock that goes backwards never leaves the tower stuck busy")
    void clockBackwards() {
        assertTrue(HeavyWork.tryAcquire(50_000));
        assertTrue(HeavyWork.tryAcquire(1_000), "now is earlier than the recorded slot: the record is dropped");
    }

    @Test
    @DisplayName("a burst is spread out: ten requests in a second are admitted one per window")
    void burst() {
        int admitted = 0;
        for (long t = 0; t < 3 * HeavyWork.WINDOW_MILLIS; t += 100) if (HeavyWork.tryAcquire(t)) admitted++;
        assertEquals(3, admitted);
    }
}
