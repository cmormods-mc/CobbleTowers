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
    private final Map<Integer, UUID> slotOwners = new HashMap<>();
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

        int slot = nextAvailableSlot();
        return bind(runId, slot);
    }

    /**
     * Reclaims one persisted slot after restart.
     *
     * <p>This is intentionally strict: a persisted run may not silently move to another coordinate.
     * If two saved runs claim the same slot, restoration fails instead of allowing overlapping tower
     * instances to mutate the same blocks.
     */
    public TowerInstanceSlot reserve(UUID runId, int slotIndex) {
        Objects.requireNonNull(runId, "runId");
        if (slotIndex < 0 || slotIndex >= maxSlots) {
            throw new IllegalArgumentException("slotIndex is outside allocator capacity: " + slotIndex);
        }

        TowerInstanceSlot existing = byRun.get(runId);
        if (existing != null) {
            if (existing.slotIndex() != slotIndex) {
                throw new IllegalStateException("Run is already assigned to a different Tower slot");
            }
            return existing;
        }

        UUID owner = slotOwners.get(slotIndex);
        if (owner != null) {
            throw new IllegalStateException("Tower slot " + slotIndex + " is already owned by run " + owner);
        }

        freeSlots.remove(slotIndex);
        if (slotIndex >= nextSlot) nextSlot = slotIndex + 1;
        return bind(runId, slotIndex);
    }

    public Optional<TowerInstanceSlot> get(UUID runId) {
        return Optional.ofNullable(byRun.get(runId));
    }

    public TowerInstanceRegion region(TowerInstanceSlot slot) {
        Objects.requireNonNull(slot, "slot");
        int negativeHalf = strideBlocks / 2;
        int minX = Math.subtractExact(slot.originX(), negativeHalf);
        int minZ = Math.subtractExact(slot.originZ(), negativeHalf);
        int maxX = Math.addExact(minX, strideBlocks - 1);
        int maxZ = Math.addExact(minZ, strideBlocks - 1);
        return new TowerInstanceRegion(slot, minX, maxX, minZ, maxZ);
    }

    public boolean release(UUID runId) {
        TowerInstanceSlot removed = byRun.remove(runId);
        if (removed == null) return false;
        slotOwners.remove(removed.slotIndex());
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

    public int strideBlocks() {
        return strideBlocks;
    }

    public void clear() {
        byRun.clear();
        slotOwners.clear();
        freeSlots.clear();
        nextSlot = 0;
    }

    private int nextAvailableSlot() {
        if (!freeSlots.isEmpty()) return freeSlots.pollFirst();
        while (nextSlot < maxSlots && slotOwners.containsKey(nextSlot)) nextSlot++;
        if (nextSlot >= maxSlots) throw new IllegalStateException("No Tower instance slots are available");
        return nextSlot++;
    }

    private TowerInstanceSlot bind(UUID runId, int slot) {
        int column = slot % gridWidth;
        int row = slot / gridWidth;
        int originX = Math.multiplyExact(column, strideBlocks);
        int originZ = Math.multiplyExact(row, strideBlocks);

        TowerInstanceSlot allocated = new TowerInstanceSlot(runId, slot, originX, originZ);
        byRun.put(runId, allocated);
        slotOwners.put(slot, runId);
        return allocated;
    }
}
