package com.cobbletowers.instance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CellClearJobsTest {
    @BeforeEach @AfterEach
    void reset() {
        CellClearJobs.resetForTests();
        InstanceAllocator.resetForTests();
        CellTickets.resetForTests();
    }

    @Test
    void staleOrMissingLeaseCannotQueueClearingOrAcquireTickets() {
        UUID old = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        InstanceAllocator.leaseForTests(2, current);
        assertFalse(CellClearJobs.ensureClear(null, 2, old));
        assertFalse(CellClearJobs.ensureClear(null, 3, old));
        assertFalse(CellClearJobs.isClearing(2));
        assertFalse(CellClearJobs.isClearing(3));
        assertFalse(CellTickets.isHeld(2));
    }

    @Test
    void transferredLeaseCancelsOldJobBeforeAnyWorldAccess() {
        UUID old = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        InstanceAllocator.leaseForTests(2, old);
        CellClearJobs.queueForTests(2, old);
        InstanceAllocator.transferLease(2, old, current);
        CellTickets.holdForTests(2);
        assertDoesNotThrow(() -> CellClearJobs.advance(null), "a stale job must not even query the world");
        assertFalse(CellClearJobs.isClearing(2));
        assertEquals(current, InstanceAllocator.runIn(2).orElseThrow());
        assertTrue(CellTickets.isHeld(2), "canceling an old job must not release the current tenant's ticket");
    }
}
