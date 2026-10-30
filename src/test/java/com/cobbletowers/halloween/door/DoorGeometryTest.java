package com.cobbletowers.halloween.door;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.FloorBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DoorGeometryTest {

    /** The first Halloween floor's first door: 5 wide, 6 high, 2 thick. */
    private static final FloorBox OPENING = new FloorBox(67, 20, 53, 71, 25, 54);
    /** Too narrow for the Grand door: 3 wide, 6 high. */
    private static final FloorBox NARROW = new FloorBox(67, 20, 53, 69, 25, 54);

    @Test
    @DisplayName("a route opening takes the Grand door: a 3 x 5 gap in the middle, through the whole wall thickness")
    void grandGap() {
        assertTrue(DoorGeometry.grand(OPENING));
        assertEquals(69, DoorGeometry.gapX(OPENING));
        int gap = 0;
        for (int x = 67; x <= 71; x++) {
            for (int y = 20; y <= 25; y++) {
                for (int z = 53; z <= 54; z++) {
                    if (DoorGeometry.isGap(OPENING, x, y, z)) {
                        gap++;
                        assertTrue(x >= 68 && x <= 70, "a jamb stays wall: x " + x);
                        assertTrue(y <= 24, "the header stays wall: y " + y);
                    }
                }
            }
        }
        assertEquals(3 * 5 * 2, gap);
    }

    @Test
    @DisplayName("jambs, header and anything outside the opening are not gap")
    void wallAroundTheGrandGap() {
        assertFalse(DoorGeometry.isGap(OPENING, 67, 20, 53));
        assertFalse(DoorGeometry.isGap(OPENING, 71, 22, 53));
        assertFalse(DoorGeometry.isGap(OPENING, 69, 25, 53));
        assertFalse(DoorGeometry.isGap(OPENING, 69, 19, 53));
        assertFalse(DoorGeometry.isGap(OPENING, 69, 20, 55));
    }

    @Test
    @DisplayName("a narrow opening falls back to the standard 1 x 2 gap")
    void standardGap() {
        assertFalse(DoorGeometry.grand(NARROW));
        assertEquals(68, DoorGeometry.gapX(NARROW));
        int gap = 0;
        for (int x = 67; x <= 69; x++) {
            for (int y = 20; y <= 25; y++) {
                for (int z = 53; z <= 54; z++) {
                    if (DoorGeometry.isGap(NARROW, x, y, z)) {
                        gap++;
                        assertEquals(68, x);
                        assertTrue(y <= 21);
                    }
                }
            }
        }
        assertEquals(2 * 2, gap);
    }

    @Test
    @DisplayName("an opening fits a door only if it has wall either side and a lintel above")
    void fits() {
        assertTrue(DoorGeometry.fits(OPENING));
        assertTrue(DoorGeometry.fits(NARROW));
        assertFalse(DoorGeometry.fits(new FloorBox(67, 20, 53, 68, 25, 54)));
        assertFalse(DoorGeometry.fits(new FloorBox(67, 20, 53, 71, 20, 54)));
    }

    @Test
    @DisplayName("an opening too short for a header over the Grand gap keeps the standard door")
    void shortOpening() {
        assertFalse(DoorGeometry.grand(new FloorBox(67, 20, 53, 71, 23, 54)));
    }
}
