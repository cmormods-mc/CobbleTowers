package com.cobbletowers.halloween.door;

import com.cobbletowers.definition.FloorBox;

/**
 * Where the gap is in a route door opening: the opening is walled and the gap in the middle takes the door. A 5 x 6 opening (every route door)
 * gets the Grand door 3 x 5 gap; a smaller one the standard 1 x 2. Pure, so it is unit-tested.
 */
public final class DoorGeometry {

    public static final int GAP_HEIGHT = 2;
    /** The Grand door clear opening: 3 wide (half-width 1 either side of the middle), 5 high. */
    public static final int GRAND_HALF_WIDTH = 1, GRAND_HEIGHT = 5;

    private DoorGeometry() {}

    /** Whether this opening takes the Grand door: 5 wide and 6 high leaves a jamb either side and a header above its 3 x 5 gap. */
    public static boolean grand(FloorBox opening) {
        return opening.maxX() - opening.minX() >= 4 && opening.maxY() - opening.minY() >= GRAND_HEIGHT;
    }

    /** The x of the gap column: the middle of the opening. */
    public static int gapX(FloorBox opening) {
        return (opening.minX() + opening.maxX()) / 2;
    }

    /** Whether the block at (x, y, z) of the opening is in the gap (every layer of the wall thickness). */
    public static boolean isGap(FloorBox opening, int x, int y, int z) {
        if (z < opening.minZ() || z > opening.maxZ() || y < opening.minY()) return false;
        if (grand(opening)) return Math.abs(x - gapX(opening)) <= GRAND_HALF_WIDTH && y < opening.minY() + GRAND_HEIGHT;
        return x == gapX(opening) && y < opening.minY() + GAP_HEIGHT;
    }

    /** True when an opening can hold the door: wide enough for wall either side of the gap and tall enough for a lintel. */
    public static boolean fits(FloorBox opening) {
        return opening.maxX() - opening.minX() >= 2 && opening.maxY() - opening.minY() >= GAP_HEIGHT;
    }
}
