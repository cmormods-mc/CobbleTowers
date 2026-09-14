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
 * loading, structure placement, and cleanup belong to later layers. Released slots are reused before
 * new indices are created, so coordinate growth is bounded by peak concurrent runs rather than total
 * historical runs.
 */
public final class TowerInstanceAllocator {
    private final int strideBlocks;
    private final int maxSlots;
    private final Map<UUID, TowerInstanceSlot> byRun = new HashMap<>();
    private final Map<Integer, UUID> bySlot = new HashMap<>();
    private final TreeSet<Integer> freeSlots = new TreeSet<>();
    private int nextSlot;

    public TowerInstanceAllocator(int strideBlocks, int maxSlots) {
        if (strideBlocks < 1) throw new IllegalArgumentException("strideBlocks must be >= 1");
        if (maxSlots < 1) throw new IllegalArgumentException("maxSlots must be >= 1");
        this.strideBlocks = strideBlocks;
        this.maxSlots = maxSlots;
    }

    public TowerInstanceSlot allocate(UUID runId) {
        Objects.requireNonNull(runId, "runId");
        TowerInstanceSlot existing = byRun.get(runId);
        if (existing != null) return existing;
        if (byRun.size() >= maxSlots) throw new IllegalStateException("No Tower instance slots are available");

        int slot = freeSlots.isEmpty() ? nextSlot++ : freeSlots.pollFirst();
        int originX = Math.multiplyExact(slot, strideBlocks);
        TowerInstanceSlot allocated = new TowerInstanceSlot(runId, slot, originX, 0);
        byRun.put(runId, allocated);
        bySlot.put(slot, runId);
        return allocated;
    }

    public Optional<TowerInstanceSlot> get(UUID runId) {
        return Optional.ofNullable(byRun.get(runId));
    }

    public boolean release(UUID runId) {
        TowerInstanceSlot removed = byRun.remove(runId);
        if (removed == null) return false;
        bySlot.remove(removed.slotIndex());
        freeSlots.add(removed.slotIndex());
        return true;
    }

    public int activeCount() {
        return byRun.size();
    }

    public int capacity() {
        return maxSlots;
    }

    public void clear() {
        byRun.clear();
        bySlot.clear();
        freeSlots.clear();
        nextSlot = 0;
    }
}
