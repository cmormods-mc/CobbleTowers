package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TDS #60's "structured developer diagnostics keyed by run/encounter": rolling stats and per-run snapshots. */
class TowerDiagnosticsStoreTest {

    private static final UUID RUN_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID RUN_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");

    @Test
    @DisplayName("a fresh category reports zero, not a division by zero")
    void freshCategoryIsEmpty() {
        assertEquals(0, new TowerDiagnosticsStore().categoryStats("allocation").count());
        assertEquals(0, new TowerDiagnosticsStore().categoryStats("allocation").averageMillis());
    }

    @Test
    @DisplayName("samples accumulate into count, sum, min, max and the over-budget tally")
    void samplesAccumulate() {
        TowerDiagnosticsStore store = new TowerDiagnosticsStore();
        store.sample("allocation", 100, false);
        store.sample("allocation", 300, true);
        store.sample("allocation", 50, false);

        TowerDiagnosticsStore.CategoryStats stats = store.categoryStats("allocation");
        assertEquals(3, stats.count());
        assertEquals(450, stats.sum());
        assertEquals(50, stats.min());
        assertEquals(300, stats.max());
        assertEquals(150, stats.averageMillis());
        assertEquals(1, stats.budgetExceededCount());
    }

    @Test
    @DisplayName("categories are independent")
    void categoriesAreIndependent() {
        TowerDiagnosticsStore store = new TowerDiagnosticsStore();
        store.sample("allocation", 100, false);

        assertEquals(0, store.categoryStats("cleanup").count());
    }

    @Test
    @DisplayName("a run's transition and encounter-construction timings are tracked separately")
    void runDiagnosticsTrackBothTimings() {
        TowerDiagnosticsStore store = new TowerDiagnosticsStore();
        store.recordTransition(RUN_A, 40, 1000L);
        store.recordEncounterConstruction(RUN_A, 900, 1005L);

        TowerDiagnosticsStore.RunDiagnostics diagnostics = store.runDiagnostics(RUN_A);
        assertEquals(40, diagnostics.lastTransitionMillis());
        assertEquals(900, diagnostics.lastEncounterConstructionMillis());
        assertEquals(1005L, diagnostics.updatedAt());
    }

    @Test
    @DisplayName("a later sample overwrites the last one, not accumulates it")
    void laterSampleOverwrites() {
        TowerDiagnosticsStore store = new TowerDiagnosticsStore();
        store.recordTransition(RUN_A, 40, 1000L);
        store.recordTransition(RUN_A, 60, 2000L);

        assertEquals(60, store.runDiagnostics(RUN_A).lastTransitionMillis());
        assertEquals(2000L, store.runDiagnostics(RUN_A).updatedAt());
    }

    @Test
    @DisplayName("runs are independent")
    void runsAreIndependent() {
        TowerDiagnosticsStore store = new TowerDiagnosticsStore();
        store.recordTransition(RUN_A, 40, 1000L);

        assertEquals(0, store.runDiagnostics(RUN_B).lastTransitionMillis());
    }

    @Test
    @DisplayName("the non-checkpointed write count rises on a write and clears on a checkpoint")
    void persistenceBacklogTracksToCheckpoint() {
        TowerDiagnosticsStore store = new TowerDiagnosticsStore();
        store.recordNonCheckpointedWrite();
        store.recordNonCheckpointedWrite();
        assertEquals(2, store.nonCheckpointedWrites());

        store.recordCheckpoint();
        assertEquals(0, store.nonCheckpointedWrites());
    }

    @Test
    @DisplayName("past the cap, the least-recently-updated run is evicted for a new one")
    void evictsTheOldestRunPastTheCap() {
        TowerDiagnosticsStore store = new TowerDiagnosticsStore();
        for (int i = 0; i < TowerDiagnosticsStore.MAX_RUN_ENTRIES; i++) {
            store.recordTransition(UUID.randomUUID(), 1, i);
        }
        store.recordTransition(RUN_A, 1, 0L);   // the oldest possible updatedAt; should be evicted first
        assertFalse(store.runDiagnostics(RUN_A).equals(TowerDiagnosticsStore.RunDiagnostics.EMPTY));

        store.recordTransition(RUN_B, 1, TowerDiagnosticsStore.MAX_RUN_ENTRIES + 1L);
        assertEquals(TowerDiagnosticsStore.RunDiagnostics.EMPTY, store.runDiagnostics(RUN_A),
                "the oldest-updated entry should have been evicted to make room");
        assertTrue(store.runDiagnostics(RUN_B).lastTransitionMillis() > 0);
    }

    @Test
    @DisplayName("categories, runs and the backlog count survive a save and load")
    void roundTrip() {
        TowerDiagnosticsStore store = new TowerDiagnosticsStore();
        store.sample("allocation", 120, false);
        store.sample("transition", 40, false);
        store.recordTransition(RUN_A, 40, 1000L);
        store.recordEncounterConstruction(RUN_A, 900, 1005L);
        store.recordNonCheckpointedWrite();

        TowerDiagnosticsStore restored = TowerDiagnosticsStore.load(store.save(new CompoundTag(), null), null);

        assertEquals(store.categoryStats("allocation"), restored.categoryStats("allocation"));
        assertEquals(store.categoryStats("transition"), restored.categoryStats("transition"));
        assertEquals(store.runDiagnostics(RUN_A), restored.runDiagnostics(RUN_A));
        assertEquals(1, restored.nonCheckpointedWrites());
    }
}
