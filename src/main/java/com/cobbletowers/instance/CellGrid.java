package com.cobbletowers.instance;

import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * Where a cell is. Index in, coordinates out, and nothing else.
 *
 * <p>One cell per 512-block square, which is exactly one region file (32x32 chunks). That is what
 * "region/chunk-aware" (TDS #26) buys: two cells can never share a chunk, so nothing one run does can
 * load, tick or corrupt another's, and a cell's storage is separable because it is a region of its
 * own. The interior is centred inside its square with a {@value #BUFFER}-block margin on every side,
 * so a structure that overruns slightly still cannot reach the neighbour.
 *
 * <p>Pure: no world, no server, no state. Every claim here is a unit test, including the inverse,
 * which is what arena containment will ask (TDS #38) once players can stand in a cell.
 */
public final class CellGrid {

    /** One region file. Cells never share a region, let alone a chunk. */
    public static final int CELL_SPAN = 512;

    /** The usable square inside a cell's span. */
    public static final int INTERIOR = 256;

    /** Dead space between one cell's interior and its span's edge. */
    public static final int BUFFER = (CELL_SPAN - INTERIOR) / 2;

    /** Cells are laid out row-major, this many per row. */
    public static final int CELLS_PER_ROW = 64;

    /** So the grid stays inside sane coordinates: 64 x 64 cells is 32,768 blocks square. */
    public static final int MAX_CELLS = CELLS_PER_ROW * CELLS_PER_ROW;

    /** Matches the dimension's min_y and height. */
    public static final int MIN_Y = 0;
    public static final int MAX_Y = 384;

    /** Where a floor sits, and where anything entering a cell is put down. */
    public static final int FLOOR_Y = 64;

    /**
     * How far below the cell a cleanup sweep still looks.
     *
     * <p>The tower is a void dimension, so anything left behind does not stay where it was left --
     * it falls, and it keeps falling below y=0 for some seconds before the void finally removes it.
     * A sweep bounded by the cell's own floor therefore reports "clean" for exactly the case it
     * exists to catch. Found live: a pig summoned in a cell was at y=-28 a moment later, and the
     * cell verified clean with it plainly there.
     */
    public static final int SWEEP_BELOW = 320;

    private CellGrid() {}

    public static void requireValid(int index) {
        if (index < 0 || index >= MAX_CELLS) {
            throw new IllegalArgumentException("cell index " + index + " is outside 0.." + (MAX_CELLS - 1));
        }
    }

    /** The north-west corner of the cell's interior. */
    public static BlockPos originOf(int index) {
        requireValid(index);
        int column = index % CELLS_PER_ROW;
        int row = index / CELLS_PER_ROW;
        return new BlockPos(column * CELL_SPAN + BUFFER, FLOOR_Y, row * CELL_SPAN + BUFFER);
    }

    /** The middle of the cell's interior, at floor height: where a player is put down. */
    public static BlockPos centerOf(int index) {
        BlockPos origin = originOf(index);
        return origin.offset(INTERIOR / 2, 0, INTERIOR / 2);
    }

    /** The whole interior column, floor to ceiling: where gameplay happens. */
    public static AABB boundsOf(int index) {
        BlockPos origin = originOf(index);
        return new AABB(origin.getX(), MIN_Y, origin.getZ(),
                origin.getX() + INTERIOR, MAX_Y, origin.getZ() + INTERIOR);
    }

    /**
     * The volume a cleanup sweep covers: the interior, plus the void beneath it where anything left
     * in a cell ends up. Wider than {@link #boundsOf} on purpose -- see {@link #SWEEP_BELOW}.
     */
    public static AABB sweepBoundsOf(int index) {
        BlockPos origin = originOf(index);
        return new AABB(origin.getX(), MIN_Y - SWEEP_BELOW, origin.getZ(),
                origin.getX() + INTERIOR, MAX_Y, origin.getZ() + INTERIOR);
    }

    /**
     * Which cell contains this position, or empty for the buffer between cells and anywhere outside
     * the grid. Deliberately not "the nearest cell": something standing in the gap belongs to no run,
     * and saying otherwise would hand one run's cleanup a position in another's neighbourhood.
     */
    public static OptionalInt indexAt(double x, double z) {
        int column = Math.floorDiv((int) Math.floor(x), CELL_SPAN);
        int row = Math.floorDiv((int) Math.floor(z), CELL_SPAN);
        if (column < 0 || column >= CELLS_PER_ROW || row < 0 || row >= CELLS_PER_ROW) return OptionalInt.empty();

        int localX = (int) Math.floor(x) - column * CELL_SPAN;
        int localZ = (int) Math.floor(z) - row * CELL_SPAN;
        if (localX < BUFFER || localX >= BUFFER + INTERIOR) return OptionalInt.empty();
        if (localZ < BUFFER || localZ >= BUFFER + INTERIOR) return OptionalInt.empty();
        return OptionalInt.of(row * CELLS_PER_ROW + column);
    }

    /** The chunks the interior covers, for the ticket work P4 owns. */
    public static ChunkPos minChunk(int index) {
        BlockPos origin = originOf(index);
        return new ChunkPos(origin);
    }

    public static ChunkPos maxChunk(int index) {
        BlockPos origin = originOf(index);
        return new ChunkPos(origin.offset(INTERIOR - 1, 0, INTERIOR - 1));
    }
}
