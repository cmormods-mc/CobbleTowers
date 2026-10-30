package com.cobbletowers.halloween.door;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DoorMotionTest {

    @Test
    @DisplayName("the opening curve hits its knots: 0, 26 at tick 2, the 105 overshoot at tick 5, 87 at tick 8, 90 at tick 12")
    void knots() {
        assertEquals(0, DoorMotion.openingDegrees(0), 1e-9);
        assertEquals(26, DoorMotion.openingDegrees(2), 1e-9);
        assertEquals(105, DoorMotion.openingDegrees(5), 1e-9);
        assertEquals(87, DoorMotion.openingDegrees(8), 1e-9);
        assertEquals(90, DoorMotion.openingDegrees(12), 1e-9);
        assertEquals(90, DoorMotion.openingDegrees(40), 1e-9);
    }

    @Test
    @DisplayName("opening takes 12 ticks and ends open at 90; closing takes 10 and ends shut at 0")
    void openAndClose() {
        DoorMotion door = new DoorMotion(false);
        assertEquals(DoorMotion.State.CLOSED, door.state());
        assertFalse(door.tick());
        door.setOpen(true);
        int ticks = 0;
        while (door.tick()) ticks++;
        assertEquals(DoorMotion.OPEN_TICKS, ticks);
        assertEquals(90, door.angle(), 1e-9);
        assertEquals(DoorMotion.State.OPEN, door.state());
        door.setOpen(false);
        ticks = 0;
        while (door.tick()) ticks++;
        assertEquals(DoorMotion.CLOSE_TICKS, ticks);
        assertEquals(0, door.angle(), 1e-9);
        assertEquals(DoorMotion.State.CLOSED, door.state());
    }

    @Test
    @DisplayName("a reversal mid-swing starts from the current angle, without a jump")
    void reversal() {
        DoorMotion door = new DoorMotion(false);
        door.setOpen(true);
        for (int i = 0; i < 4; i++) door.tick();
        double before = door.angle();
        assertTrue(before > 0 && before < 90 + 20);
        door.setOpen(false);
        assertEquals(before, door.angle(), 1e-9);
        door.tick();
        assertTrue(door.angle() <= before);
        while (door.tick()) { /* run out */ }
        assertEquals(0, door.angle(), 1e-9);
    }

    @Test
    @DisplayName("a door built open stands at 90 and does not move")
    void builtOpen() {
        DoorMotion door = new DoorMotion(true);
        assertEquals(90, door.angle(), 1e-9);
        assertEquals(DoorMotion.State.OPEN, door.state());
        assertFalse(door.tick());
    }
}
