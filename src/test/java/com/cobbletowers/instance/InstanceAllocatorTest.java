package com.cobbletowers.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which cell gets handed out, and which never does.
 *
 * <p>The choice is tested here without a server; the rest of allocation is a map write and the live
 * test drives it end to end.
 */
class InstanceAllocatorTest {

    private static final IntPredicate NOTHING_QUARANTINED = cell -> false;
    private static final UUID RUN_A = UUID.fromString("aaaaaaaa-1111-0000-0000-000000000001");
    private static final UUID RUN_B = UUID.fromString("bbbbbbbb-1111-0000-0000-000000000002");

    @BeforeEach
    @AfterEach
    void clear() {
        InstanceAllocator.resetForTests();
    }

    @Test
    @DisplayName("an empty tower hands out cell zero")
    void firstCell() {
        assertEquals(OptionalInt.of(0), InstanceAllocator.nextFree(NOTHING_QUARANTINED));
    }

    @Test
    @DisplayName("a leased cell is skipped, and the lowest free one is taken")
    void skipsLeased() {
        InstanceAllocator.leaseForTests(0, RUN_A);
        InstanceAllocator.leaseForTests(1, RUN_B);

        assertEquals(OptionalInt.of(2), InstanceAllocator.nextFree(NOTHING_QUARANTINED));
    }

    @Test
    @DisplayName("a gap is reused before the far end of the grid")
    void reusesGaps() {
        InstanceAllocator.leaseForTests(0, RUN_A);
        InstanceAllocator.leaseForTests(2, RUN_B);

        assertEquals(OptionalInt.of(1), InstanceAllocator.nextFree(NOTHING_QUARANTINED),
                "cells are a pool, not a queue: the hole between two tenants is the next one out");
    }

    @Test
    @DisplayName("a quarantined cell is never handed out, however free it looks")
    void quarantinedIsNeverChosen() {
        // The whole point of TDS #35: the cell is not leased, so nothing else would stop it being
        // reused. Only the quarantine does.
        Set<Integer> quarantined = Set.of(0, 1, 2);

        assertEquals(OptionalInt.of(3), InstanceAllocator.nextFree(quarantined::contains));
    }

    @Test
    @DisplayName("clearing a quarantine puts the cell back at the front of the queue")
    void clearingReturnsTheCell() {
        Set<Integer> quarantined = new java.util.HashSet<>(Set.of(0));
        assertEquals(OptionalInt.of(1), InstanceAllocator.nextFree(quarantined::contains));

        quarantined.clear();

        assertEquals(OptionalInt.of(0), InstanceAllocator.nextFree(quarantined::contains));
    }

    @Test
    @DisplayName("a tower with every cell leased or quarantined hands out nothing")
    void exhausted() {
        for (int cell = 0; cell < CellGrid.MAX_CELLS; cell++) {
            InstanceAllocator.leaseForTests(cell, UUID.randomUUID());
        }

        assertEquals(OptionalInt.empty(), InstanceAllocator.nextFree(NOTHING_QUARANTINED),
                "running out is a refusal, not a wrap-around onto somebody else's cell");
    }

    @Test
    @DisplayName("the index answers both ways round")
    void indexesBothWays() {
        InstanceAllocator.leaseForTests(7, RUN_A);

        assertEquals(OptionalInt.of(7), InstanceAllocator.cellOf(RUN_A));
        assertEquals(java.util.Optional.of(RUN_A), InstanceAllocator.runIn(7));
        assertEquals(1, InstanceAllocator.leasedCount());
        assertTrue(InstanceAllocator.cellOf(RUN_B).isEmpty());
        assertTrue(InstanceAllocator.runIn(8).isEmpty());
    }

    @Test
    @DisplayName("shutdown empties the index, which is rebuilt from the runs next start")
    void shutdownClears() {
        InstanceAllocator.leaseForTests(3, RUN_A);

        assertEquals(1, InstanceAllocator.onServerStopped());

        assertEquals(0, InstanceAllocator.leasedCount());
        assertEquals(OptionalInt.of(0), InstanceAllocator.nextFree(NOTHING_QUARANTINED));
    }
}
