package com.cobbletowers.instance;

import com.cobbletowers.TowerLog;
import com.cobbletowers.persistence.CellQuarantine;
import com.cobbletowers.persistence.CellStateStore;
import com.cobbletowers.persistence.PersistedRun;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Who holds which cell.
 *
 * <p>Indexed both ways (TDS #34): cell to run and run to cell are map hits, and the next free cell
 * is the first clear bit of a {@link BitSet} rather than a walk over every cell asking whether it is
 * busy.
 *
 * <p>The authority for a lease is the run itself -- {@link PersistedRun#cell()} -- and this index is
 * rebuilt from the runs at start. There is therefore no second file that can disagree with the first
 * about who holds what, and a lease cannot outlive the run that owns it.
 *
 * <p>Quarantine is the exception and lives in {@link CellStateStore}, because a cell taken out of
 * service has to stay out after its run is gone.
 */
public final class InstanceAllocator {

    /** Cells currently leased. Rebuilt from the runs, never read from a file of its own. */
    private static final BitSet LEASED = new BitSet(CellGrid.MAX_CELLS);
    private static final Map<Integer, UUID> RUN_BY_CELL = new HashMap<>();
    private static final Map<UUID, Integer> CELL_BY_RUN = new HashMap<>();

    private InstanceAllocator() {}

    /** Why a cell could not be handed out. */
    public enum Refusal {
        /** Every cell is either leased or quarantined. */
        NO_CELL_FREE,
        /** The datapack dimension is missing, so there is nowhere to put anything. */
        NO_DIMENSION,
        /** The run already holds one. Allocating twice would strand the first. */
        ALREADY_LEASED
    }

    public sealed interface Allocation permits Leased, Denied {}

    public record Leased(int cell) implements Allocation {}

    public record Denied(Refusal refusal) implements Allocation {}

    /** What happened when a cell was given back. */
    public sealed interface Release permits Released, Quarantined {}

    public record Released(int cell) implements Release {}

    public record Quarantined(int cell, String reason) implements Release {}

    /**
     * Rebuilds the index from the runs that were loaded.
     *
     * <p>Two runs claiming one cell cannot happen through the allocator, but it can happen through a
     * hand-edited save, so it is checked here rather than assumed: the second claimant is left
     * without a cell and the cell is quarantined, because at that point nobody can say which run's
     * contents are in it.
     */
    public static int rebuild(MinecraftServer server, Collection<PersistedRun> runs) {
        LEASED.clear();
        RUN_BY_CELL.clear();
        CELL_BY_RUN.clear();
        CellStateStore cells = CellStateStore.get(server);

        for (PersistedRun run : runs) {
            OptionalInt held = run.cell();
            if (held.isEmpty()) continue;
            int cell = held.getAsInt();
            UUID existing = RUN_BY_CELL.get(cell);
            if (existing != null) {
                TowerLog.error("Runs {} and {} both claim cell {}; quarantining it and leaving {} without one",
                        existing, run.runId(), cell, run.runId());
                cells.quarantine(server, new CellQuarantine(cell,
                        "two runs claimed it: " + existing + " and " + run.runId(), System.currentTimeMillis()));
                continue;
            }
            LEASED.set(cell);
            RUN_BY_CELL.put(cell, run.runId());
            CELL_BY_RUN.put(run.runId(), cell);
        }
        return CELL_BY_RUN.size();
    }

    /**
     * The lowest cell that is neither leased nor quarantined.
     *
     * <p>Separated from {@link #allocate} so the choice can be tested without a server, and so the
     * scan is one place: {@link BitSet#nextClearBit} jumps straight to a free index rather than
     * asking every cell in turn whether it is busy.
     */
    static OptionalInt nextFree(java.util.function.IntPredicate quarantined) {
        for (int cell = LEASED.nextClearBit(0); cell < CellGrid.MAX_CELLS; cell = LEASED.nextClearBit(cell + 1)) {
            if (!quarantined.test(cell)) return OptionalInt.of(cell);
        }
        return OptionalInt.empty();
    }

    /** The lowest cell that is neither leased nor quarantined, handed to {@code runId}. */
    public static Allocation allocate(MinecraftServer server, UUID runId) {
        if (!TowerDimension.isLoaded(server)) return new Denied(Refusal.NO_DIMENSION);
        if (CELL_BY_RUN.containsKey(runId)) return new Denied(Refusal.ALREADY_LEASED);

        CellStateStore cells = CellStateStore.get(server);
        OptionalInt free = nextFree(cells::isQuarantined);
        if (free.isEmpty()) return new Denied(Refusal.NO_CELL_FREE);

        int cell = free.getAsInt();
        LEASED.set(cell);
        RUN_BY_CELL.put(cell, runId);
        CELL_BY_RUN.put(runId, cell);
        TowerLog.info("Cell {} leased to run {}", cell, runId);
        return new Leased(cell);
    }

    /**
     * Gives a cell back, verifying it first.
     *
     * <p>A cell that does not verify is quarantined rather than freed. The lease is dropped either
     * way: the run is finished with it, and leaving the lease in place would make the cell look busy
     * rather than broken -- which is the state nobody can act on.
     */
    public static Release release(MinecraftServer server, UUID runId, int cell) {
        CellGrid.requireValid(cell);
        try {
            CellCleanup.Report report = CellCleanup.verify(server, cell);
            if (report.isClean()) {
                TowerLog.info("Cell {} released by run {}", cell, runId);
                return new Released(cell);
            }
            CellStateStore.get(server).quarantine(server,
                    new CellQuarantine(cell, report.summary(), System.currentTimeMillis()));
            return new Quarantined(cell, report.summary());
        } finally {
            // Must run whatever the verification did, including throwing: a lease nothing releases is
            // the one leak the allocator cannot recover from, because no later event refers to it.
            LEASED.clear(cell);
            RUN_BY_CELL.remove(cell, runId);
            CELL_BY_RUN.remove(runId, cell);
        }
    }

    /** Takes a cell out of service by hand. */
    public static void quarantine(MinecraftServer server, int cell, String reason) {
        CellGrid.requireValid(cell);
        CellStateStore.get(server).quarantine(server,
                new CellQuarantine(cell, reason, System.currentTimeMillis()));
    }

    /** Returns a quarantined cell to service. False when it was not quarantined. */
    public static boolean clearQuarantine(MinecraftServer server, int cell) {
        CellGrid.requireValid(cell);
        return CellStateStore.get(server).clear(server, cell);
    }

    public static OptionalInt cellOf(UUID runId) {
        Integer cell = CELL_BY_RUN.get(runId);
        return cell == null ? OptionalInt.empty() : OptionalInt.of(cell);
    }

    public static Optional<UUID> runIn(int cell) {
        return Optional.ofNullable(RUN_BY_CELL.get(cell));
    }

    public static int leasedCount() {
        return CELL_BY_RUN.size();
    }

    /** Memory hygiene at shutdown; the index is rebuilt from the runs on the next start. */
    public static int onServerStopped() {
        int held = CELL_BY_RUN.size();
        LEASED.clear();
        RUN_BY_CELL.clear();
        CELL_BY_RUN.clear();
        return held;
    }

    /** Test seam: the index without a server. */
    static void resetForTests() {
        LEASED.clear();
        RUN_BY_CELL.clear();
        CELL_BY_RUN.clear();
    }

    /** Test seam: lease a specific cell without a server or a store. */
    static void leaseForTests(int cell, UUID runId) {
        LEASED.set(cell);
        RUN_BY_CELL.put(cell, runId);
        CELL_BY_RUN.put(runId, cell);
    }
}
