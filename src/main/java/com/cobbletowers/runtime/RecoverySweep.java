package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.diagnostics.TowerMetrics;
import com.cobbletowers.instance.CellCleanup;
import com.cobbletowers.instance.CellGrid;
import com.cobbletowers.instance.CellTickets;
import com.cobbletowers.instance.TowerDimension;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Cleaning up after a crash, in the cell the crash left behind.
 *
 * <p>The gap P5 wrote down rather than solved: a server that goes down mid-floor strands whatever
 * was fighting -- a tower opponent as readily as a player's own Pokemon -- and the cell keeps them.
 * Nothing noticed until the run finally ended, when the release swept the cell, found entities and
 * quarantined it. Correct behaviour, reporting somebody else's mess, and one cell lost per crash.
 *
 * <p>Why this is not four lines inside {@link RunRecovery}: at the moment recovery runs, the tower
 * dimension holds no tickets at all, so the cell's chunks are not loaded -- and an unloaded chunk
 * reports no entities. A sweep there would return "clean" every time and be believed. So the cell's
 * tickets are taken, the sweep waits until the chunks and their entities have actually arrived, and
 * only then looks.
 */
public final class RecoverySweep {

    /**
     * How long to wait for a cell's chunks before giving up on it.
     *
     * <p>Thirty seconds at 20 ticks a second, which is far longer than loading seven chunks takes on
     * anything. Giving up is not silent: it says so, and the old safety net -- the quarantine at
     * release -- is still there behind it.
     */
    static final int LOAD_TIMEOUT_TICKS = 600;

    /**
     * Ticks to wait after the chunks report loaded, before sweeping.
     *
     * <p>A chunk being loaded and its entities being loaded are two different moments: the entity
     * sections arrive a tick or two behind. Sweeping on the first is how a sweep reports clean and
     * leaves a Pokemon standing in the cell.
     */
    static final int SETTLE_TICKS = 40;

    private record Pending(UUID runId, int waited, int settled) {}

    private static final Map<Integer, Pending> PENDING = new LinkedHashMap<>();

    private RecoverySweep() {}

    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty()) return;
            long started = System.nanoTime();
            try {
                tick(server);
            } catch (RuntimeException ex) {
                TowerLog.error("A tower recovery sweep failed", ex);
            } finally {
                TowerMetrics.recordTick(server, "recovery sweep", (System.nanoTime() - started) / 1_000_000);
            }
        });
    }

    /** Asks for this cell to be swept as soon as it can honestly be looked at. */
    public static void schedule(MinecraftServer server, UUID runId, int cell) {
        if (PENDING.containsKey(cell)) return;
        CellTickets.hold(server, cell);
        PENDING.put(cell, new Pending(runId, 0, 0));
        TowerLog.info("Cell {} of interrupted run {} will be swept once its chunks are loaded", cell, runId);
    }

    private static void tick(MinecraftServer server) {
        for (Map.Entry<Integer, Pending> entry : Map.copyOf(PENDING).entrySet()) {
            int cell = entry.getKey();
            Pending pending = entry.getValue();

            if (!chunksLoaded(server, cell)) {
                if (pending.waited() + 1 >= LOAD_TIMEOUT_TICKS) {
                    TowerLog.error("Cell {} of run {} never loaded, so it could not be swept; anything left in it"
                            + " will be found when the run finally releases the cell", cell, pending.runId());
                    finish(server, cell);
                } else {
                    PENDING.put(cell, new Pending(pending.runId(), pending.waited() + 1, 0));
                }
                continue;
            }
            if (pending.settled() < SETTLE_TICKS) {
                PENDING.put(cell, new Pending(pending.runId(), pending.waited(), pending.settled() + 1));
                continue;
            }

            int swept = CellCleanup.sweepEntities(server, cell);
            if (swept > 0) {
                TowerLog.info("Cell {} of interrupted run {} had {} entity/entities left in it; removed",
                        cell, pending.runId(), swept);
            }
            finish(server, cell);
        }
    }

    private static void finish(MinecraftServer server, int cell) {
        PENDING.remove(cell);
        // The run is parked, not running: holding its chunks after the sweep would be a ticket with
        // nothing using it, which is exactly what TDS #27 forbids.
        CellTickets.drop(server, cell);
    }

    /** Whether the cell's arena chunks -- the ones the ticket covers -- are actually there. */
    private static boolean chunksLoaded(MinecraftServer server, int cell) {
        ServerLevel level = TowerDimension.level(server);
        if (level == null) return false;
        BlockPos centre = CellGrid.centerOf(cell);
        int centreX = centre.getX() >> 4;
        int centreZ = centre.getZ() >> 4;
        for (int x = centreX - CellTickets.RADIUS_CHUNKS; x <= centreX + CellTickets.RADIUS_CHUNKS; x++) {
            for (int z = centreZ - CellTickets.RADIUS_CHUNKS; z <= centreZ + CellTickets.RADIUS_CHUNKS; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    public static int pending() {
        return PENDING.size();
    }

    public static void onServerStopped() {
        PENDING.clear();
    }
}
