package com.cobbletowers.instance;

import com.cobbletowers.ServerState;
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
 * Keeps a cell's chunks loaded while something uses it (TDS #27). A region ticket, never a forceload (which survives
 * a restart). Covers the arena (49 chunks), not the whole cell.
 */
public final class CellTickets {

    private static final TicketType<ChunkPos> CELL =
            TicketType.create("cobbletowers_cell", Comparator.comparingLong(ChunkPos::toLong));

    /**
     * Chunks either side of the arena centre: 3 gives a 7x7 area, enough for the 51-block arena, and within the
     * ticking threshold.
     */
    public static final int RADIUS_CHUNKS = 3;

    private static final Set<Integer> HELD = new LinkedHashSet<>();

    static {
        ServerState.onStop(CellTickets::onServerStopped);
    }

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

    /** Whether this mod still holds the cell's chunks; the third cleanup stage (TDS section 11). */
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

    /** Drops every ticket at shutdown so an integrated client's next world does not think it holds chunks. */
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
