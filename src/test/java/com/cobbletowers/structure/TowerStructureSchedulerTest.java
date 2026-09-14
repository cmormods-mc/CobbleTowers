package com.cobbletowers.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class TowerStructureSchedulerTest {

    @Test
    void executesAtMostOneSectionGloballyPerTick() {
        AtomicInteger calls = new AtomicInteger();
        TowerStructureScheduler scheduler = new TowerStructureScheduler(
                (runId, slot, section, operation) -> {
                    calls.incrementAndGet();
                    return TowerStructureOperationResult.SUCCESS;
                },
                snapshot -> {},
                (runId, section) -> {}
        );

        scheduler.enqueueBuild(UUID.randomUUID(), 0, TowerCellProgress.allocated());
        scheduler.enqueueBuild(UUID.randomUUID(), 1, TowerCellProgress.allocated());

        scheduler.tick(1, 20);
        assertEquals(1, calls.get());
        scheduler.tick(2, 20);
        assertEquals(2, calls.get());
    }

    @Test
    void retriesBuildThreeTimesThenQuarantines() {
        UUID runId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<TowerStructureScheduler.CellWorkSnapshot> latest = new AtomicReference<>();
        TowerStructureScheduler scheduler = new TowerStructureScheduler(
                (id, slot, section, operation) -> {
                    calls.incrementAndGet();
                    return TowerStructureOperationResult.RETRYABLE_FAILURE;
                },
                latest::set,
                (id, section) -> {}
        );

        scheduler.enqueueBuild(runId, 0, TowerCellProgress.allocated());
        scheduler.tick(1, 20);
        scheduler.tick(2, 20);
        scheduler.tick(3, 20);

        assertEquals(3, calls.get());
        assertEquals(TowerCellState.QUARANTINED, latest.get().progress().state());
        assertFalse(scheduler.get(runId).isPresent());
    }

    @Test
    void containsRuntimeExceptionAndTreatsItAsRetryable() {
        UUID runId = UUID.randomUUID();
        AtomicReference<TowerStructureScheduler.CellWorkSnapshot> latest = new AtomicReference<>();
        TowerStructureScheduler scheduler = new TowerStructureScheduler(
                (id, slot, section, operation) -> { throw new IllegalStateException("synthetic failure"); },
                latest::set,
                (id, section) -> {}
        );

        scheduler.enqueueBuild(runId, 0, TowerCellProgress.allocated());
        scheduler.tick(10, 20);

        assertEquals(1, latest.get().progress().failedAttempts());
        assertEquals(TowerCellState.BUILDING, latest.get().progress().state());
        assertTrue(scheduler.get(runId).isPresent());
    }

    @Test
    void assetFaultQuarantinesImmediatelyAndNotifiesBoundary() {
        UUID runId = UUID.randomUUID();
        AtomicReference<TowerStructureScheduler.CellWorkSnapshot> latest = new AtomicReference<>();
        List<String> faults = new ArrayList<>();
        TowerStructureScheduler scheduler = new TowerStructureScheduler(
                (id, slot, section, operation) -> TowerStructureOperationResult.ASSET_FAULT,
                latest::set,
                (id, section) -> faults.add(id + ":" + section.id())
        );

        scheduler.enqueueBuild(runId, 0, TowerCellProgress.allocated());
        scheduler.tick(1, 20);

        assertEquals(TowerCellState.QUARANTINED, latest.get().progress().state());
        assertEquals(1, faults.size());
        assertFalse(scheduler.get(runId).isPresent());
    }

    @Test
    void cleanupFailureRemainsQueuedAndNeverCompletesSlotRelease() {
        UUID runId = UUID.randomUUID();
        AtomicReference<TowerStructureScheduler.CellWorkSnapshot> latest = new AtomicReference<>();
        TowerCellProgress ready = TowerCellProgress.allocated().beginBuild();
        for (int i = 0; i < TowerStructureSection.ordered().size(); i++) ready = ready.recordBuildSuccess();
        TowerCellProgress active = ready.activate();

        TowerStructureScheduler scheduler = new TowerStructureScheduler(
                (id, slot, section, operation) -> TowerStructureOperationResult.RETRYABLE_FAILURE,
                latest::set,
                (id, section) -> {}
        );

        scheduler.enqueueCleanup(runId, 0, active, 0);
        scheduler.tick(1, 40);

        assertEquals(TowerCellState.TEARDOWN, latest.get().progress().state());
        assertEquals(1, latest.get().progress().failedAttempts());
        assertTrue(scheduler.get(runId).isPresent());
    }

    @Test
    void restartResumesPersistedSectionCursorWithoutPersistingTickDeadline() {
        UUID runId = UUID.randomUUID();
        TowerCellProgress building = TowerCellProgress.allocated().beginBuild().recordBuildSuccess();
        TowerStructureScheduler.CellWorkSnapshot persisted =
                new TowerStructureScheduler.CellWorkSnapshot(runId, 3, building);
        AtomicReference<TowerStructureSection> executed = new AtomicReference<>();

        TowerStructureScheduler scheduler = new TowerStructureScheduler(
                (id, slot, section, operation) -> {
                    executed.set(section);
                    return TowerStructureOperationResult.SUCCESS;
                },
                snapshot -> {},
                (id, section) -> {}
        );

        scheduler.restore(persisted, 500);
        scheduler.tick(500, 20);

        assertEquals(TowerStructureSection.CORE, executed.get());
    }
}
