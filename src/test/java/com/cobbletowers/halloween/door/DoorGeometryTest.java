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

    @Test
    @DisplayName("the gap is the middle column, two blocks high, through the whole wall thickness")
    void gap() {
        assertEquals(69, DoorGeometry.gapX(OPENING));
        int gap = 0;
        for (int x = 67; x <= 71; x++) {
            for (int y = 20; y <= 25; y++) {
                for (int z = 53; z <= 54; z++) {
                    if (DoorGeometry.isGap(OPENING, x, y, z)) {
                        gap++;
                        assertEquals(69, x);
                        assertTrue(y <= 21);
                    }
                }
            }
        }
        assertEquals(2 * 2, gap);
    }

    @Test
    @DisplayName("above the gap is lintel wall, and outside the opening is no gap")
    void lintel() {
        assertFalse(DoorGeometry.isGap(OPENING, 69, 22, 53));
        assertFalse(DoorGeometry.isGap(OPENING, 68, 20, 53));
        assertFalse(DoorGeometry.isGap(OPENING, 69, 19, 53));
        assertFalse(DoorGeometry.isGap(OPENING, 69, 20, 55));
    }

    @Test
    @DisplayName("an opening fits the door only if it has wall either side and a lintel above")
    void fits() {
        assertTrue(DoorGeometry.fits(OPENING));
        assertFalse(DoorGeometry.fits(new FloorBox(67, 20, 53, 68, 25, 54)));
        assertFalse(DoorGeometry.fits(new FloorBox(67, 20, 53, 71, 20, 54)));
    }
}
