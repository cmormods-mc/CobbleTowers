package com.cobbletowers.rental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RentalScalingTest {

    @Test
    @DisplayName("a rental starts at 50 and gains 10 levels every three floors")
    void steps() {
        for (int floor = 1; floor <= 3; floor++) assertEquals(50, RentalScaling.levelFor(floor), "floor " + floor);
        for (int floor = 4; floor <= 6; floor++) assertEquals(60, RentalScaling.levelFor(floor), "floor " + floor);
        for (int floor = 7; floor <= 9; floor++) assertEquals(70, RentalScaling.levelFor(floor), "floor " + floor);
        assertEquals(80, RentalScaling.levelFor(10));
    }

    @Test
    @DisplayName("it never passes 100, however deep an ascending tower goes, and never falls")
    void capped() {
        assertEquals(100, RentalScaling.levelFor(31));
        assertEquals(100, RentalScaling.levelFor(5000));
        int last = 0;
        for (int floor = 1; floor <= 200; floor++) {
            int level = RentalScaling.levelFor(floor);
            assertTrue(level >= last && level <= 100, "floor " + floor);
            last = level;
        }
    }

    @Test
    @DisplayName("every step is an AscensionLib milestone, so each one earns exactly one upgrade credit")
    void stepsAreMilestones() {
        for (int floor = 1; floor <= 40; floor++) assertEquals(0, RentalScaling.levelFor(floor) % 10, "floor " + floor);
    }
}
