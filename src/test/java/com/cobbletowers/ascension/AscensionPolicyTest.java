package com.cobbletowers.ascension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AscensionPolicyTest {

    @Test
    @DisplayName("floors map onto cycles: floor 11 of ten is Ascension 1, tower floor 1")
    void mapping() {
        assertEquals(0, AscensionPolicy.ascensionOf(1, 10));
        assertEquals(0, AscensionPolicy.ascensionOf(10, 10));
        assertEquals(1, AscensionPolicy.ascensionOf(11, 10));
        assertEquals(2, AscensionPolicy.ascensionOf(25, 10));
        assertEquals(1, AscensionPolicy.towerFloorOf(1, 10));
        assertEquals(10, AscensionPolicy.towerFloorOf(10, 10));
        assertEquals(1, AscensionPolicy.towerFloorOf(11, 10));
        assertEquals(5, AscensionPolicy.towerFloorOf(25, 10));
        assertEquals(10, AscensionPolicy.towerFloorOf(30, 10));
    }

    @Test
    @DisplayName("a cycle ends on every multiple of the floor count, and an Ascension starts the floor after")
    void cycleEnds() {
        assertTrue(AscensionPolicy.isCycleEnd(10, 10));
        assertTrue(AscensionPolicy.isCycleEnd(20, 10));
        assertFalse(AscensionPolicy.isCycleEnd(11, 10));
        assertEquals(1, AscensionPolicy.firstFloorOf(0, 10));
        assertEquals(11, AscensionPolicy.firstFloorOf(1, 10));
        assertEquals(41, AscensionPolicy.firstFloorOf(4, 10));
        for (int a = 0; a < 20; a++) {
            assertEquals(a, AscensionPolicy.ascensionOf(AscensionPolicy.firstFloorOf(a, 4), 4));
            assertEquals(1, AscensionPolicy.towerFloorOf(AscensionPolicy.firstFloorOf(a, 4), 4));
        }
    }

    @Test
    @DisplayName("nonsense input degrades to the base cycle instead of throwing")
    void degenerate() {
        assertEquals(0, AscensionPolicy.ascensionOf(5, 0));
        assertEquals(0, AscensionPolicy.ascensionOf(0, 10));
        assertEquals(3, AscensionPolicy.towerFloorOf(3, 0));
        assertFalse(AscensionPolicy.isCycleEnd(0, 10));
    }

    @Test
    @DisplayName("nothing grows at the base cycle")
    void baseIsNeutral() {
        assertEquals(100, AscensionPolicy.bossHealthPercent(0));
        assertEquals(0, AscensionPolicy.extraOpponents(0));
        assertEquals(100, AscensionPolicy.rewardPercent(0));
        assertEquals(0, AscensionPolicy.enemyEvs(0));
        assertEquals(0, AscensionPolicy.boonEvs(0));
    }

    @Test
    @DisplayName("boss health and enemy EVs keep growing; opponents stop at the cap")
    void difficultyGrows() {
        assertEquals(112, AscensionPolicy.bossHealthPercent(1));
        assertEquals(220, AscensionPolicy.bossHealthPercent(10));
        assertEquals(0, AscensionPolicy.extraOpponents(2));
        assertEquals(1, AscensionPolicy.extraOpponents(3));
        assertEquals(3, AscensionPolicy.extraOpponents(9));
        assertEquals(3, AscensionPolicy.extraOpponents(500), "capped");
        assertEquals(50, AscensionPolicy.enemyEvs(1));
        assertEquals(1000, AscensionPolicy.enemyEvs(20));
        assertEquals(4000, AscensionPolicy.enemyEvs(10_000), "never more than a stat can hold");
    }

    @Test
    @DisplayName("the player boon is half the enemy's")
    void boon() {
        assertEquals(25, AscensionPolicy.boonEvs(1));
        assertEquals(500, AscensionPolicy.boonEvs(20));
    }

    @Test
    @DisplayName("the reward factor rises with shrinking steps, and never passes its ceiling (TDS #9)")
    void rewardsAreBounded() {
        int previous = 100;
        int previousStep = 1000;
        for (int a = 1; a <= 60; a++) {
            int now = AscensionPolicy.rewardPercent(a);
            assertTrue(now >= previous, "never falls: " + a);
            int step = now - previous;
            assertTrue(step <= previousStep + 1, "steps shrink, give or take integer rounding: " + a);
            assertTrue(now <= 250, "under the ceiling: " + a + " -> " + now);
            previous = now;
            previousStep = step;
        }
        assertEquals(115, AscensionPolicy.rewardPercent(1));
        assertEquals(161, AscensionPolicy.rewardPercent(5));
        assertTrue(AscensionPolicy.rewardPercent(18) > 220, "still climbing through the range a strong team plays");
        assertTrue(AscensionPolicy.rewardPercent(1000) <= 250);
        assertTrue(AscensionPolicy.rewardPercent(Integer.MAX_VALUE) <= 250);
    }
}
