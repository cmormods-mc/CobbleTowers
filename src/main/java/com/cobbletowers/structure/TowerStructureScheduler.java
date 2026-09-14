package com.cobbletowers.structure;

import com.cobbletowers.CobbleTowers;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Server-thread scheduler for bounded Tower structure build/cleanup work.
 *
 * <p>At most one structure section operation is executed globally per tick. Unexpected runtime
 * exceptions are contained here and never intentionally propagated into the Minecraft server tick
 * loop. Build work receives three attempts total. Cleanup work remains quarantined and retryable;
 * the production retry cadence is supplied by the caller so policy is not hidden in this class.
 */
public final class TowerStructureScheduler {
    public static final int MAX_BUILD_ATTEMPTS = 3;

    private final TowerStructureExecutor executor;
    private final Consumer<CellWorkSnapshot> persistenceSink;
    private final BiConsumer<UUID, TowerStructureSection> assetFaultSink;
    private final ArrayDeque<UUID> queue = new ArrayDeque<>();
    private final Map<UUID, CellWork> byRun = new HashMap<>();

    public TowerStructureScheduler(
            TowerStructureExecutor executor,
            Consumer<CellWorkSnapshot> persistenceSink,
            BiConsumer<UUID, TowerStructureSection> assetFaultSink
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.persistenceSink = Objects.requireNonNull(persistenceSink, "persistenceSink");
        this.assetFaultSink = Objects.requireNonNull(assetFaultSink, "assetFaultSink");
    }

    public void enqueueBuild(UUID runId, int slotIndex, TowerCellProgress progress) {
        Objects.requireNonNull(progress, "progress");
        TowerCellProgress normalized = progress.state() == TowerCellState.ALLOCATED
                ? progress.beginBuild()
                : progress;
        if (normalized.state() != TowerCellState.BUILDING) {
            throw new IllegalArgumentException("Build work requires ALLOCATED or BUILDING state, got " + normalized.state());
        }
        put(new CellWork(runId, slotIndex, normalized, 0));
    }

    public void enqueueCleanup(UUID runId, int slotIndex, TowerCellProgress progress, long firstEligibleTick) {
        Objects.requireNonNull(progress, "progress");
        TowerCellProgress normalized = progress.state() == TowerCellState.TEARDOWN
                ? progress
                : progress.beginTeardown();
        put(new CellWork(runId, slotIndex, normalized, Math.max(0, firstEligibleTick)));
    }

    /**
     * Executes zero or one bounded structure operation.
     *
     * @param serverTick monotonically increasing online server tick used only for retry eligibility.
     */
    public void tick(long serverTick, long cleanupRetryDelayTicks) {
        if (queue.isEmpty()) return;
        if (cleanupRetryDelayTicks < 1) {
            throw new IllegalArgumentException("cleanupRetryDelayTicks must be >= 1");
        }

        int candidates = queue.size();
        while (candidates-- > 0) {
            UUID runId = queue.removeFirst();
            CellWork work = byRun.get(runId);
            if (work == null) continue;
            if (serverTick < work.nextEligibleTick()) {
                queue.addLast(runId);
                continue;
            }

            performOne(work, serverTick, cleanupRetryDelayTicks);
            return; // hard global budget: at most one operation per tick
        }
    }

    public Optional<CellWorkSnapshot> get(UUID runId) {
        CellWork work = byRun.get(runId);
        return work == null ? Optional.empty() : Optional.of(work.snapshot());
    }

    public int queuedCellCount() {
        return byRun.size();
    }

    private void performOne(CellWork work, long serverTick, long cleanupRetryDelayTicks) {
        TowerCellProgress progress = work.progress();
        TowerStructureOperation operation = progress.state() == TowerCellState.BUILDING
                ? TowerStructureOperation.BUILD
                : TowerStructureOperation.CLEANUP;
        TowerStructureSection section = progress.currentSection();

        TowerStructureOperationResult result;
        try {
            result = Objects.requireNonNull(
                    executor.execute(work.runId(), work.slotIndex(), section, operation),
                    "TowerStructureExecutor returned null"
            );
        } catch (RuntimeException ex) {
            CobbleTowers.LOGGER.error(
                    "Contained Tower structure exception: run={}, slot={}, section={}, operation={}",
                    work.runId(), work.slotIndex(), section.id(), operation, ex
            );
            result = TowerStructureOperationResult.RETRYABLE_FAILURE;
        }

        switch (result) {
            case SUCCESS -> onSuccess(work, operation);
            case RETRYABLE_FAILURE -> onRetryableFailure(work, operation, serverTick, cleanupRetryDelayTicks);
            case ASSET_FAULT -> onAssetFault(work, section);
        }
    }

    private void onSuccess(CellWork work, TowerStructureOperation operation) {
        TowerCellProgress next = operation == TowerStructureOperation.BUILD
                ? work.progress().recordBuildSuccess()
                : work.progress().recordCleanupSuccess();
        CellWork updated = new CellWork(work.runId(), work.slotIndex(), next, 0);
        if (next.state() == TowerCellState.READY || next.cleanupComplete()) {
            byRun.remove(work.runId());
            persistenceSink.accept(updated.snapshot());
            return;
        }
        replaceAndRequeue(updated);
    }

    private void onRetryableFailure(
            CellWork work,
            TowerStructureOperation operation,
            long serverTick,
            long cleanupRetryDelayTicks
    ) {
        TowerCellProgress failed = work.progress().recordFailure();
        if (operation == TowerStructureOperation.BUILD && failed.failedAttempts() >= MAX_BUILD_ATTEMPTS) {
            CellWork quarantined = new CellWork(work.runId(), work.slotIndex(), failed.quarantine(), 0);
            byRun.remove(work.runId());
            persistenceSink.accept(quarantined.snapshot());
            CobbleTowers.LOGGER.error(
                    "Tower cell quarantined after {} failed build attempts: run={}, slot={}, section={}",
                    failed.failedAttempts(), work.runId(), work.slotIndex(), work.progress().currentSection().id()
            );
            return;
        }

        long nextEligible = operation == TowerStructureOperation.BUILD
                ? Math.addExact(serverTick, 1)
                : Math.addExact(serverTick, cleanupRetryDelayTicks);
        replaceAndRequeue(new CellWork(work.runId(), work.slotIndex(), failed, nextEligible));
    }

    private void onAssetFault(CellWork work, TowerStructureSection section) {
        CellWork quarantined = new CellWork(work.runId(), work.slotIndex(), work.progress().quarantine(), 0);
        byRun.remove(work.runId());
        persistenceSink.accept(quarantined.snapshot());
        assetFaultSink.accept(work.runId(), section);
        CobbleTowers.LOGGER.error(
                "Non-retryable Tower structure asset fault: run={}, slot={}, section={}",
                work.runId(), work.slotIndex(), section.id()
        );
    }

    private void put(CellWork work) {
        Objects.requireNonNull(work.runId(), "runId");
        if (work.slotIndex() < 0) throw new IllegalArgumentException("slotIndex must be >= 0");
        if (byRun.putIfAbsent(work.runId(), work) != null) {
            throw new IllegalStateException("Structure work already queued for run " + work.runId());
        }
        queue.addLast(work.runId());
        persistenceSink.accept(work.snapshot());
    }

    private void replaceAndRequeue(CellWork work) {
        byRun.put(work.runId(), work);
        queue.addLast(work.runId());
        persistenceSink.accept(work.snapshot());
    }

    private record CellWork(UUID runId, int slotIndex, TowerCellProgress progress, long nextEligibleTick) {
        private CellWork {
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(progress, "progress");
            if (slotIndex < 0) throw new IllegalArgumentException("slotIndex must be >= 0");
            if (nextEligibleTick < 0) throw new IllegalArgumentException("nextEligibleTick must be >= 0");
        }

        private CellWorkSnapshot snapshot() {
            return new CellWorkSnapshot(runId, slotIndex, progress, nextEligibleTick);
        }
    }

    public record CellWorkSnapshot(UUID runId, int slotIndex, TowerCellProgress progress, long nextEligibleTick) {
        public CellWorkSnapshot {
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(progress, "progress");
            if (slotIndex < 0) throw new IllegalArgumentException("slotIndex must be >= 0");
            if (nextEligibleTick < 0) throw new IllegalArgumentException("nextEligibleTick must be >= 0");
        }
    }
}
