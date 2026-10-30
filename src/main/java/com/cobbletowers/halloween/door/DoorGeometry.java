package com.cobbletowers.halloween.door;

import com.cobbletowers.definition.FloorBox;

/** Where the gap is in a route door opening: the opening is walled and a 1 x 2 gap in the middle takes the door. Pure, so it is unit-tested. */
public final class DoorGeometry {

    public static final int GAP_HEIGHT = 2;

    private DoorGeometry() {}

    /** The x of the gap column: the middle of the opening. */
    public static int gapX(FloorBox opening) {
        return (opening.minX() + opening.maxX()) / 2;
    }

    /** Whether the block at (x, y, z) of the opening is in the gap (every layer of the wall thickness). */
    public static boolean isGap(FloorBox opening, int x, int y, int z) {
        return x == gapX(opening) && y >= opening.minY() && y < opening.minY() + GAP_HEIGHT
                && z >= opening.minZ() && z <= opening.maxZ();
    }

    /** True when an opening can hold the door: wide enough for wall either side of the gap and tall enough for a lintel. */
    public static boolean fits(FloorBox opening) {
        return opening.maxX() - opening.minX() >= 2 && opening.maxY() - opening.minY() >= GAP_HEIGHT;
    }
}
