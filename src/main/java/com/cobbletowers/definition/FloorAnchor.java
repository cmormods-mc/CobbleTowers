package com.cobbletowers.definition;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

/**
 * A spot inside a floor's structure, in the structure's own coordinates.
 *
 * <p>Relative, not absolute: the same floor is pasted into whichever cell a run is given, so an
 * anchor that named a world position would be right for exactly one run. {@link #in} turns it into a
 * world position once the paste origin is known.
 *
 * @param yaw which way someone placed here faces, in degrees
 */
public record FloorAnchor(int x, int y, int z, float yaw) {

    public static FloorAnchor fromJson(JsonObject root, String key) {
        // Checked by presence rather than by what comes back: an absent anchor otherwise reads as an
        // empty object and fails on the first field inside it, so the complaint names 'x' instead of
        // naming the anchor nobody wrote.
        if (!root.has(key)) {
            throw new IllegalArgumentException("layout is missing the '" + key + "' anchor");
        }
        JsonObject object = TowerJson.object(root, key);
        return new FloorAnchor(
                TowerJson.requireInt(object, "x"),
                TowerJson.requireInt(object, "y"),
                TowerJson.requireInt(object, "z"),
                (float) TowerJson.integer(object, "yaw", 0));
    }

    /** This anchor as a world position, given where the structure was pasted. */
    public BlockPos in(BlockPos origin) {
        return origin.offset(x, y, z);
    }
}
