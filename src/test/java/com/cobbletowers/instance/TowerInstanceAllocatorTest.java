package com.cobbletowers.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class TowerInstanceAllocatorTest {

    @Test
    void allocatesCompactGridOriginsAndReusesReleasedSlots() {
        TowerInstanceAllocator allocator = new TowerInstanceAllocator(192, 4);
        UUID firstRun = UUID.randomUUID();
        UUID secondRun = UUID.randomUUID();
        UUID thirdRun = UUID.randomUUID();

        TowerInstanceSlot first = allocator.allocate(firstRun);
        TowerInstanceSlot second = allocator.allocate(secondRun);

        assertEquals(2, allocator.gridWidth());
        assertEquals(0, first.slotIndex());
        assertEquals(0, first.originX());
        assertEquals(0, first.originZ());
        assertEquals(1, second.slotIndex());
        assertEquals(192, second.originX());
        assertEquals(0, second.originZ());

        assertTrue(allocator.release(firstRun));
        TowerInstanceSlot reused = allocator.allocate(thirdRun);
        assertEquals(0, reused.slotIndex());
        assertEquals(0, reused.originX());
        assertEquals(0, reused.originZ());
    }

    @Test
    void movesToNextGridRowWithoutOverlappingOrigins() {
        TowerInstanceAllocator allocator = new TowerInstanceAllocator(192, 20);
        Set<String> origins = new HashSet<>();
        TowerInstanceSlot sixth = null;

        for (int i = 0; i < 20; i++) {
            TowerInstanceSlot slot = allocator.allocate(UUID.randomUUID());
            assertTrue(origins.add(slot.originX() + ":" + slot.originZ()));
            if (i == 5) sixth = slot;
        }

        assertEquals(5, allocator.gridWidth());
        assertEquals(0, sixth.originX());
        assertEquals(192, sixth.originZ());
        assertEquals(20, origins.size());
    }

    @Test
    void allocationIsIdempotentForOneRun() {
        TowerInstanceAllocator allocator = new TowerInstanceAllocator(192, 2);
        UUID runId = UUID.randomUUID();

        TowerInstanceSlot first = allocator.allocate(runId);
        TowerInstanceSlot second = allocator.allocate(runId);

        assertSame(first, second);
        assertEquals(1, allocator.activeCount());
    }

    @Test
    void enforcesCapacityWithoutGrowingCoordinates() {
        TowerInstanceAllocator allocator = new TowerInstanceAllocator(192, 2);
        allocator.allocate(UUID.randomUUID());
        allocator.allocate(UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> allocator.allocate(UUID.randomUUID()));
        assertEquals(2, allocator.activeCount());
    }

    @Test
    void releaseIsSafeForUnknownRun() {
        TowerInstanceAllocator allocator = new TowerInstanceAllocator(192, 2);
        assertFalse(allocator.release(UUID.randomUUID()));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TowerInstanceAllocator(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TowerInstanceAllocator(1, 0));
    }
}
