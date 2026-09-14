package com.cobbletowers.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.instance.TowerInstanceAllocator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class TowerRunManagerTest {

    @Test
    void createsAndIndexesRunByParticipantWithoutScanning() {
        TowerRunManager manager = new TowerRunManager(new TowerInstanceAllocator(192, 20));
        UUID player = UUID.randomUUID();
        TowerRun run = manager.create(UUID.randomUUID(), 42L, 10, List.of(participant(player)));

        assertEquals(run.runId(), manager.forPlayer(player).orElseThrow().runId());
        assertEquals(1, manager.activeRunCount());
    }

    @Test
    void preventsOnePlayerFromJoiningTwoActiveRuns() {
        TowerRunManager manager = new TowerRunManager(new TowerInstanceAllocator(192, 20));
        UUID player = UUID.randomUUID();
        manager.create(UUID.randomUUID(), 1L, 10, List.of(participant(player)));

        assertThrows(IllegalStateException.class,
                () -> manager.create(UUID.randomUUID(), 2L, 10, List.of(participant(player))));
    }

    @Test
    void restoreReclaimsExactPersistedSlot() {
        TowerRunManager firstManager = new TowerRunManager(new TowerInstanceAllocator(192, 20));
        UUID runId = UUID.randomUUID();
        TowerRun original = firstManager.create(runId, 99L, 10, List.of(participant(UUID.randomUUID())));
        TowerRunSnapshot snapshot = original.snapshot();

        TowerRunManager restoredManager = new TowerRunManager(new TowerInstanceAllocator(192, 20));
        TowerRun restored = restoredManager.restore(snapshot);

        assertEquals(snapshot.slotIndex(), restored.instanceSlot().slotIndex());
        assertEquals(original.instanceSlot().originX(), restored.instanceSlot().originX());
        assertEquals(original.instanceSlot().originZ(), restored.instanceSlot().originZ());
    }

    @Test
    void conflictingPersistedSlotsFailRatherThanOverlap() {
        TowerRunManager manager = new TowerRunManager(new TowerInstanceAllocator(192, 20));
        TowerRun first = manager.create(UUID.randomUUID(), 1L, 10, List.of(participant(UUID.randomUUID())));
        TowerRunSnapshot conflicting = new TowerRunSnapshot(
                UUID.randomUUID(),
                2L,
                10,
                first.instanceSlot().slotIndex(),
                1,
                TowerRunState.PREPARING,
                List.of(participant(UUID.randomUUID())),
                0,
                false,
                null,
                List.of(),
                java.util.Map.of()
        );

        assertThrows(IllegalStateException.class, () -> manager.restore(conflicting));
        assertEquals(1, manager.activeRunCount());
    }

    @Test
    void releaseDropsParticipantIndexAndReleasesSlot() {
        TowerInstanceAllocator allocator = new TowerInstanceAllocator(192, 2);
        TowerRunManager manager = new TowerRunManager(allocator);
        UUID player = UUID.randomUUID();
        TowerRun run = manager.create(UUID.randomUUID(), 1L, 10, List.of(participant(player)));
        int releasedSlot = run.instanceSlot().slotIndex();

        assertTrue(manager.release(run.runId()));
        assertTrue(manager.forPlayer(player).isEmpty());
        assertEquals(0, manager.activeRunCount());

        TowerRun replacement = manager.create(UUID.randomUUID(), 2L, 10, List.of(participant(UUID.randomUUID())));
        assertEquals(releasedSlot, replacement.instanceSlot().slotIndex());
        assertFalse(manager.release(UUID.randomUUID()));
    }

    private static TowerParticipant participant(UUID playerId) {
        return new TowerParticipant(
                playerId,
                new TowerReturnLocation("minecraft:overworld", 0.5, 64.0, 0.5, 0f, 0f),
                true
        );
    }
}
