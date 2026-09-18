package com.cobbletowers.instance;

import com.cobbletowers.definition.FloorAnchor;
import com.cobbletowers.definition.FloorLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Whether a floor's declared anchors are places a player could actually be (TDS section 12).
 *
 * <p>The anchors are hand-written, because the schematics carry no marker blocks to derive them
 * from. `validate_definitions.py` checks them against the committed structure file before anything
 * ships; this checks them against the blocks that were actually pasted, which is the version that
 * still holds when a structure is replaced, rotated, or pasted somewhere unexpected.
 *
 * <p>Three questions per anchor, and they are the three that make the difference between a playable
 * floor and a player stuck inside a pillar: is it in its own cell, is there something to stand on,
 * and is there room to stand.
 */
public final class CellAnchors {

    private CellAnchors() {}

    /** Every problem with every anchor, empty when the floor is fit to play. */
    public static List<String> validate(ServerLevel level, int cell, BlockPos origin, FloorLayout layout) {
        List<String> problems = new ArrayList<>();
        for (String name : layout.anchorNames()) {
            FloorAnchor anchor = layout.anchors().get(name);
            BlockPos where = anchor.in(origin);

            OptionalInt containing = CellGrid.indexAt(where.getX(), where.getZ());
            if (containing.isEmpty() || containing.getAsInt() != cell) {
                // Either in the gap between cells or, worse, inside a neighbour's.
                problems.add(name + " at " + where + " is not inside cell " + cell);
                continue;
            }
            if (!level.getBlockState(where.below()).isFaceSturdy(level, where.below(), Direction.UP)) {
                problems.add(name + " at " + where + " has nothing solid to stand on");
            }
            if (!level.getBlockState(where).getCollisionShape(level, where).isEmpty()) {
                problems.add(name + " at " + where + " is inside a block");
            }
            if (!level.getBlockState(where.above()).getCollisionShape(level, where.above()).isEmpty()) {
                problems.add(name + " at " + where + " has no headroom");
            }
        }
        return problems;
    }
}
