package com.cobbletowers.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TDS #67 A: a jersey number is generated from the encounter seed, not rolled. */
class JerseyNumbersTest {

    @Test
    @DisplayName("the same seed always gives the same number")
    void deterministic() {
        assertEquals(JerseyNumbers.forEncounter(123456789L), JerseyNumbers.forEncounter(123456789L));
    }

    @Test
    @DisplayName("always in sports jersey range, including for negative and extreme seeds")
    void inRange() {
        long[] seeds = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, -8_123_456_789_012_345L};
        for (long seed : seeds) {
            int number = JerseyNumbers.forEncounter(seed);
            assertTrue(number >= JerseyNumbers.MIN && number <= JerseyNumbers.MAX,
                    "seed " + seed + " produced out-of-range number " + number);
        }
    }
}
