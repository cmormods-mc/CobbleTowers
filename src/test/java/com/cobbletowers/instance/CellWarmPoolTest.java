package com.cobbletowers.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Handing over a cell that is already built, and never handing over one that is not. */
class CellWarmPoolTest {

    private static final ResourceLocation ARENA =
            ResourceLocation.fromNamespaceAndPath("cobbletowers", "arena_floor");
    private static final ResourceLocation BOSS =
            ResourceLocation.fromNamespaceAndPath("cobbletowers", "boss_arena");
    private static final UUID RUN = UUID.fromString("dddddddd-2222-0000-0000-000000000001");

    @BeforeEach
    @AfterEach
    void clear() {
        CellWarmPool.resetForTests();
        InstanceAllocator.resetForTests();
    }

    @Test
    @DisplayName("a waiting cell is handed over, and stops being warm once it is")
    void takeHandsOver() {
        CellWarmPool.addForTests(ARENA, 7);
        assertTrue(CellWarmPool.isWarm(7));

        assertEquals(OptionalInt.of(7), CellWarmPool.take(ARENA, RUN));

        assertFalse(CellWarmPool.isWarm(7), "a cell being played in is not also waiting to be played in");
        assertEquals(0, CellWarmPool.readyCount());
        assertEquals(OptionalInt.of(7), InstanceAllocator.cellOf(RUN), "and the lease moved with it");
    }

    @Test
    @DisplayName("the pool is kept per structure, so a boss floor never gets an ordinary arena")
    void perStructure() {
        // The whole reason warming is worth anything: the cell is handed over already built, which
        // is only true if it was built for the floor asking for it.
        CellWarmPool.addForTests(ARENA, 1);

        assertEquals(OptionalInt.empty(), CellWarmPool.take(BOSS, RUN),
                "a cell built as an ordinary arena is no use to a boss floor");
        assertEquals(OptionalInt.of(1), CellWarmPool.take(ARENA, RUN));
    }

    @Test
    @DisplayName("an empty pool says so rather than inventing a cell")
    void emptyPool() {
        assertEquals(OptionalInt.empty(), CellWarmPool.take(ARENA, RUN));
        assertEquals(0, CellWarmPool.readyCount(ARENA));
    }

    @Test
    @DisplayName("cells come out in the order they went in")
    void firstInFirstOut() {
        CellWarmPool.addForTests(ARENA, 3);
        CellWarmPool.addForTests(ARENA, 9);

        assertEquals(OptionalInt.of(3), CellWarmPool.take(ARENA, RUN));
        assertEquals(OptionalInt.of(9), CellWarmPool.take(ARENA, UUID.randomUUID()));
    }

    @Test
    @DisplayName("counts are reported per structure and in total")
    void counts() {
        CellWarmPool.addForTests(ARENA, 1);
        CellWarmPool.addForTests(ARENA, 2);
        CellWarmPool.addForTests(BOSS, 3);

        assertEquals(2, CellWarmPool.readyCount(ARENA));
        assertEquals(1, CellWarmPool.readyCount(BOSS));
        assertEquals(3, CellWarmPool.readyCount());
    }

    @Test
    @DisplayName("shutdown empties the pool")
    void shutdownClears() {
        CellWarmPool.addForTests(ARENA, 1);
        CellWarmPool.addForTests(BOSS, 2);

        assertEquals(2, CellWarmPool.onServerStopped());

        assertEquals(0, CellWarmPool.readyCount());
        assertFalse(CellWarmPool.isWarm(1));
    }
}
