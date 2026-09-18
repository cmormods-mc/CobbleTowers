package com.cobbletowers.instance;

import com.cobbletowers.TowerLog;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Keeps a cell's chunks loaded for exactly as long as something is using it (TDS #27).
 *
 * <p>A region ticket, deliberately, and never {@code setChunkForced}: a forceload is written into
 * the world and survives a restart, which is precisely what "no active run implies no tower-owned
 * chunk tickets" forbids. Tickets live in memory, so a server that comes back from a crash holds
 * none at all -- the invariant is true by construction rather than by remembering to clean up.
 *
 * <p>The ticket covers the <b>arena</b>, not the whole cell. A cell's interior is 16 chunks square;
 * an arena is about four. Loading the interior would mean 289 chunks ticking per run to hold a
 * building that fits in 49, which is the kind of cost nobody notices until there are twenty runs.
 */
public final class CellTickets {

    private static final TicketType<ChunkPos> CELL =
            TicketType.create("cobbletowers_cell", Comparator.comparingLong(ChunkPos::toLong));

    /**
     * Chunks either side of the arena centre. Three gives a 7x7 area, 112 blocks square, which holds
     * the 51-block arena with room to spare. The resulting ticket level is well inside the ticking
     * threshold, so entities in an arena tick while a run is in it.
     */
    public static final int RADIUS_CHUNKS = 3;

    private static final Set<Integer> HELD = new LinkedHashSet<>();

    private CellTickets() {}

    private static ChunkPos centreChunk(int cell) {
        return new ChunkPos(CellGrid.centerOf(cell));
    }

    /** Loads the cell's arena and keeps it loaded. Repeat calls are harmless. */
    public static void hold(MinecraftServer server, int cell) {
        CellGrid.requireValid(cell);
        ServerLevel level = TowerDimension.level(server);
        if (level == null) return;
        if (!HELD.add(cell)) return;
        level.getChunkSource().addRegionTicket(CELL, centreChunk(cell), RADIUS_CHUNKS, centreChunk(cell));
        TowerLog.info("Cell {} chunks held", cell);
    }

    /** Lets the cell's chunks go. Safe to call for a cell nothing was holding. */
    public static void drop(MinecraftServer server, int cell) {
        CellGrid.requireValid(cell);
        if (!HELD.remove(cell)) return;
        ServerLevel level = TowerDimension.level(server);
        if (level == null) return;
        level.getChunkSource().removeRegionTicket(CELL, centreChunk(cell), RADIUS_CHUNKS, centreChunk(cell));
        TowerLog.info("Cell {} chunks released", cell);
    }

    /**
     * Whether this mod is still holding the cell's chunks.
     *
     * <p>The third cleanup stage (TDS section 11). A cell nobody has let go of is not reusable, and
     * before this existed that stage was left out rather than stubbed, because a check that always
     * passes reads as verified and verifies nothing.
     */
    public static boolean isHeld(int cell) {
        return HELD.contains(cell);
    }

    /** How many cells are being held loaded, for the diagnostics the TDS asks to track. */
    public static int heldCount() {
        return HELD.size();
    }

    /** The chunks one held cell covers, for reporting a real number rather than an estimate. */
    public static int chunksPerCell() {
        int span = RADIUS_CHUNKS * 2 + 1;
        return span * span;
    }

    /** The corner a floor is pasted at, so an arena of this size sits centred in the cell. */
    public static BlockPos pasteOrigin(int cell, int width, int length) {
        BlockPos centre = CellGrid.centerOf(cell);
        return new BlockPos(centre.getX() - width / 2, CellGrid.FLOOR_Y, centre.getZ() - length / 2);
    }

    /**
     * Drops every ticket at shutdown.
     *
     * <p>Not strictly needed -- tickets do not survive the server -- but the set is static and this
     * class is not per-server, so leaving it populated would have an integrated client's next world
     * believing it holds chunks it does not.
     */
    public static int onServerStopped() {
        int held = HELD.size();
        HELD.clear();
        return held;
    }

    /** Test seam: hold a cell without a server, for the cleanup stage's own tests. */
    static void holdForTests(int cell) {
        HELD.add(cell);
    }

    static void resetForTests() {
        HELD.clear();
    }
}
