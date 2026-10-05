package com.cobbletowers.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AscensionLibRewardsTest {

    /** Milestones on tower floors 5 and 10 of a ten-floor tower, repeating each cycle. */
    private static boolean milestone(int floor) { return floor % 5 == 0; }

    @Test
    @DisplayName("the first milestone's segment starts at floor 1")
    void firstSegmentStartsAtOne() {
        assertEquals(1, AscensionLibRewards.segmentStart(AscensionLibRewardsTest::milestone, 5));
    }

    @Test
    @DisplayName("a later milestone's segment starts after the previous one, including later cycles")
    void laterSegmentsStartAfterThePreviousMilestone() {
        assertEquals(6, AscensionLibRewards.segmentStart(AscensionLibRewardsTest::milestone, 10));
        assertEquals(11, AscensionLibRewards.segmentStart(AscensionLibRewardsTest::milestone, 15));
    }

    @Test
    @DisplayName("a tower with no earlier milestone pays from floor 1")
    void noEarlierMilestone() {
        assertEquals(1, AscensionLibRewards.segmentStart(floor -> false, 7));
    }
}
