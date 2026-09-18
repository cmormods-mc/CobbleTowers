package com.cobbletowers.instance;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.FloorLayout;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Cells already built and waiting, so a party does not stand about while an arena is pasted (#26).
 *
 * <p>Kept <b>per structure</b>, because that is the only way warming saves the work that costs
 * anything. Loading a cell's chunks is nearly free in a void dimension; pasting five thousand blocks
 * is not. A pool of "prepared cells" that did not know which floor they were for would have to paste
 * again on handover, which is the whole expense it exists to avoid.
 *
 * <p>Topped up on allocation and release rather than on a tick, and never more than once every
 * {@link #COOLDOWN_MILLIS}: TDS section 11 forbids per-tick sweeps, and an event-driven top-up has
 * no idle cost at all.
 */
public final class CellWarmPool {

    /** How many ready cells to keep per structure. Two covers a party finishing as another starts. */
    public static final int TARGET_PER_STRUCTURE = 2;

    /** The shortest gap between top-ups, so a burst of runs cannot turn into a burst of pasting. */
    public static final long COOLDOWN_MILLIS = 5_000;

    /** The pool's own tenant id, so a warmed cell is leased rather than merely intended. */
    private static final UUID POOL = UUID.fromString("00000000-c0bb-1e70-0000-000000000001");

    private static final Map<ResourceLocation, Deque<Integer>> READY = new LinkedHashMap<>();
    private static long lastTopUp;

    private CellWarmPool() {}

    /**
     * A cell already built for this layout, if one is waiting.
     *
     * <p>The lease moves from the pool to the caller, who is then responsible for releasing it. A
     * cell handed out here has already been pasted and had its anchors checked.
     */
    public static OptionalInt take(ResourceLocation structure, UUID runId) {
        Deque<Integer> ready = READY.get(structure);
        if (ready == null || ready.isEmpty()) return OptionalInt.empty();
        int cell = ready.removeFirst();
        InstanceAllocator.transferLease(cell, POOL, runId);
        TowerLog.info("Cell {} handed to run {} from the warm pool", cell, runId);
        return OptionalInt.of(cell);
    }

    /**
     * Builds cells for this layout up to the target, if the cooldown has passed.
     *
     * <p>Returns how many were built. A failure to build one stops the round rather than looping:
     * whatever prevented it -- no free cell, a missing structure -- will prevent the next one too,
     * and a pool that retries hard on a broken server is worse than a pool that stays empty.
     */
    public static int topUp(MinecraftServer server, FloorLayout layout, long now) {
        if (now - lastTopUp < COOLDOWN_MILLIS) return 0;
        lastTopUp = now;

        Deque<Integer> ready = READY.computeIfAbsent(layout.structure(), key -> new ArrayDeque<>());
        int built = 0;
        while (ready.size() < TARGET_PER_STRUCTURE) {
            InstanceAllocator.Allocation allocation = InstanceAllocator.allocate(server, poolTenant(ready.size()));
            if (!(allocation instanceof InstanceAllocator.Leased leased)) break;

            int cell = leased.cell();
            var prepared = CellPreparer.prepare(server, cell, layout);
            if (prepared.isEmpty() || !prepared.get().isPlayable()) {
                // Give it straight back; release decides whether it is merely empty or actually bad.
                InstanceAllocator.release(server, poolTenant(ready.size()), cell);
                break;
            }
            InstanceAllocator.transferLease(cell, poolTenant(ready.size()), POOL);
            ready.addLast(cell);
            built++;
        }
        if (built > 0) {
            TowerLog.info("Warm pool built {} cell(s) for {}; {} ready", built, layout.structure(), ready.size());
        }
        return built;
    }

    /**
     * A distinct tenant per in-flight warm build.
     *
     * <p>The allocator refuses to lease twice to the same holder, which is the right rule for a run
     * and an awkward one for a pool building several cells in a row. Deriving the id from the pool's
     * own id keeps them distinct and recognisable in a log.
     */
    private static UUID poolTenant(int index) {
        return new UUID(POOL.getMostSignificantBits(), POOL.getLeastSignificantBits() + index);
    }

    public static int readyCount() {
        return READY.values().stream().mapToInt(Deque::size).sum();
    }

    public static int readyCount(ResourceLocation structure) {
        Deque<Integer> ready = READY.get(structure);
        return ready == null ? 0 : ready.size();
    }

    /** True while this cell is being kept ready, rather than being played in. */
    public static boolean isWarm(int cell) {
        return READY.values().stream().anyMatch(ready -> ready.contains(cell));
    }

    /** Memory hygiene at shutdown; warmed cells are simply cells again on the next start. */
    public static int onServerStopped() {
        int ready = readyCount();
        READY.clear();
        lastTopUp = 0;
        return ready;
    }

    /** Test seam. */
    static void resetForTests() {
        READY.clear();
        lastTopUp = 0;
    }

    static void addForTests(ResourceLocation structure, int cell) {
        READY.computeIfAbsent(structure, key -> new ArrayDeque<>()).addLast(cell);
    }
}
