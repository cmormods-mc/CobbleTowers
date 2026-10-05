package com.cobbletowers.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScoutingTiersTest {

    @Test
    @DisplayName("floors 1-4, 5-9 and 10 on map to the three trial tiers")
    void byFloor() {
        for (int floor = 1; floor <= 4; floor++) assertEquals("trial_rank_1", ScoutingTiers.tierFor(floor, false));
        for (int floor = 5; floor <= 9; floor++) assertEquals("trial_rank_2", ScoutingTiers.tierFor(floor, false));
        for (int floor = 10; floor <= 40; floor++) assertEquals("trial_rank_3", ScoutingTiers.tierFor(floor, false));
    }

    @Test
    @DisplayName("a milestone boss uses the boss tier on any floor, later cycles included")
    void milestoneBoss() {
        assertEquals("boss", ScoutingTiers.tierFor(5, true));
        assertEquals("boss", ScoutingTiers.tierFor(10, true));
        assertEquals("boss", ScoutingTiers.tierFor(25, true));
    }
}
