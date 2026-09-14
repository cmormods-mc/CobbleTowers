package com.cobbletowers.instance;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Server-thread allocator for private Tower regions in one shared dimension.
 *
 * <p>The allocator is intentionally world-agnostic. It owns only slot identities and origins; chunk
 * loading, structure placement, and cleanup belong to later layers. Slots are laid out on a compact
 * square-ish 2D grid so peak concurrent runs stay spatially bounded in both axes. Released slots are
 * reused before new indices are created, so coordinate growth depends on peak concurrency rather than
 * total historical runs.
 */
public final class TowerInstanceAllocator {
    private final int strideBlocks;
    private final int maxSlots;
    private final int gridWidth;
    private final Map<UUID, TowerInstanceSlot> byRun = new HashMap<>();
    private final TreeSet<Integer> freeSlots = new TreeSet<>();
    private int nextSlot;

    public TowerInstanceAllocator(int strideBlocks, int maxSlots) {
        if (strideBlocks < 1) throw new IllegalArgumentException("strideBlocks must be >= 1");
        if (maxSlots < 1) throw new IllegalArgumentException("maxSlots must be >= 1");
        this.strideBlocks = strideBlocks;
        this.maxSlots = maxSlots;
        this.gridWidth = (int) Math.ceil(Math.sqrt(maxSlots));
    }

    public TowerInstanceSlot allocate(UUID runId) {
        Objects.requireNonNull(runId, "runId");
        TowerInstanceSlot existing = byRun.get(runId);
        if (existing != null) return existing;
        if (byRun.size() >= maxSlots) throw new IllegalStateException("No Tower instance slots are available");

        int slot = freeSlots.isEmpty() ? nextSlot++ : freeSlots.pollFirst();
        int column = slot % gridWidth;
        int row = slot / gridWidth;
        int originX = Math.multiplyExact(column, strideBlocks);
        int originZ = Math.multiplyExact(row, strideBlocks);

        TowerInstanceSlot allocated = new TowerInstanceSlot(runId, slot, originX, originZ);
        byRun.put(runId, allocated);
        return allocated;
    }

    public Optional<TowerInstanceSlot> get(UUID runId) {
        return Optional.ofNullable(byRun.get(runId));
    }

    public boolean release(UUID runId) {
        TowerInstanceSlot removed = byRun.remove(runId);
        if (removed == null) return false;
        freeSlots.add(removed.slotIndex());
        return true;
    }

    public int activeCount() {
        return byRun.size();
    }

    public int capacity() {
        return maxSlots;
    }

    public int gridWidth() {
        return gridWidth;
    }

    public void clear() {
        byRun.clear();
        freeSlots.clear();
        nextSlot = 0;
    }
}
