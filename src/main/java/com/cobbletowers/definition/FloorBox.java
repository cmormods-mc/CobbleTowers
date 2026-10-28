package com.cobbletowers.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * A box of blocks inside a floor's structure, in the structure's own coordinates and inclusive at both ends (a gate to open). Like an anchor it is
 * placed from the paste origin, so the same floor works in whichever cell a run gets.
 */
public record FloorBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public FloorBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("a box needs min <= max on every axis, got (" + minX + "," + minY + "," + minZ + ") to ("
                    + maxX + "," + maxY + "," + maxZ + ")");
        }
    }

    /** The box under {@code key}, or empty when the layout has none. */
    public static Optional<FloorBox> optional(JsonObject root, String key) {
        if (!root.has(key)) return Optional.empty();
        JsonObject object = TowerJson.object(root, key);
        int[] low = corner(object, "min");
        int[] high = corner(object, "max");
        return Optional.of(new FloorBox(low[0], low[1], low[2], high[0], high[1], high[2]));
    }

    private static int[] corner(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            throw new IllegalArgumentException("a box needs a '" + key + "' corner: [x, y, z]");
        }
        JsonArray array = object.getAsJsonArray(key);
        if (array.size() != 3) throw new IllegalArgumentException("'" + key + "' must be [x, y, z], got " + array);
        return new int[] {array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt()};
    }

    public int volume() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public BlockPos minIn(BlockPos origin) {
        return origin.offset(minX, minY, minZ);
    }

    public BlockPos maxIn(BlockPos origin) {
        return origin.offset(maxX, maxY, maxZ);
    }
}
