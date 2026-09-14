package com.cobbletowers.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class TowerInstanceAllocatorTest {

    @Test
    void allocatesUniqueOriginsAndReusesReleasedSlots() {
        TowerInstanceAllocator allocator = new TowerInstanceAllocator(4096, 4);
        UUID firstRun = UUID.randomUUID();
        UUID secondRun = UUID.randomUUID();
        UUID thirdRun = UUID.randomUUID();

        TowerInstanceSlot first = allocator.allocate(firstRun);
        TowerInstanceSlot second = allocator.allocate(secondRun);

        assertEquals(0, first.slotIndex());
        assertEquals(0, first.originX());
        assertEquals(1, second.slotIndex());
        assertEquals(4096, second.originX());

        assertTrue(allocator.release(firstRun));
        TowerInstanceSlot reused = allocator.allocate(thirdRun);
        assertEquals(0, reused.slotIndex());
        assertEquals(0, reused.originX());
    }

    @Test
    void allocationIsIdempotentForOneRun() {
        TowerInstanceAllocator allocator = new TowerInstanceAllocator(2048, 2);
        UUID runId = UUID.randomUUID();

        TowerInstanceSlot first = allocator.allocate(runId);
        TowerInstanceSlot second = allocator.allocate(runId);

        assertSame(first, second);
        assertEquals(1, allocator.activeCount());
    }

    @Test
    void enforcesCapacityWithoutGrowingCoordinates() {
        TowerInstanceAllocator allocator = new TowerInstanceAllocator(1024, 2);
        allocator.allocate(UUID.randomUUID());
        allocator.allocate(UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> allocator.allocate(UUID.randomUUID()));
        assertEquals(2, allocator.activeCount());
    }

    @Test
    void releaseIsSafeForUnknownRun() {
        TowerInstanceAllocator allocator = new TowerInstanceAllocator(1024, 2);
        assertFalse(allocator.release(UUID.randomUUID()));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TowerInstanceAllocator(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TowerInstanceAllocator(1, 0));
    }
}
