package com.cobbletowers.run;

import com.cobbletowers.instance.TowerInstanceAllocator;
import com.cobbletowers.instance.TowerInstanceSlot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-thread owner of all active Tower runs.
 *
 * <p>Runs are indexed directly by run UUID and participant UUID. No global player/world scans are
 * needed to answer "which run owns this player?". Instance allocation and participant indexing are
 * created/restored/released atomically through this class. Optional persistence sinks receive one
 * immutable snapshot per durable mutation and one run ID after successful teardown/release.
 */
public final class TowerRunManager {
    private static final Consumer<TowerRunSnapshot> NO_SNAPSHOT_SINK = snapshot -> {};
    private static final Consumer<UUID> NO_REMOVE_SINK = runId -> {};

    private final TowerInstanceAllocator allocator;
    private final Consumer<TowerRunSnapshot> persistenceSink;
    private final Consumer<UUID> persistenceRemoveSink;
    private final Map<UUID, TowerRun> runs = new HashMap<>();
    private final Map<UUID, UUID> runByPlayer = new HashMap<>();

    public TowerRunManager(TowerInstanceAllocator allocator) {
        this(allocator, NO_SNAPSHOT_SINK, NO_REMOVE_SINK);
    }

    public TowerRunManager(
            TowerInstanceAllocator allocator,
            Consumer<TowerRunSnapshot> persistenceSink,
            Consumer<UUID> persistenceRemoveSink
    ) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.persistenceSink = Objects.requireNonNull(persistenceSink, "persistenceSink");
        this.persistenceRemoveSink = Objects.requireNonNull(persistenceRemoveSink, "persistenceRemoveSink");
    }

    public TowerRun create(UUID runId, long seed, int maxFloors, Collection<TowerParticipant> participants) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(participants, "participants");
        if (runs.containsKey(runId)) throw new IllegalStateException("Tower run already exists: " + runId);
        validateParticipantsAvailable(participants);

        TowerInstanceSlot slot = allocator.allocate(runId);
        try {
            TowerRun run = new TowerRun(runId, seed, maxFloors, slot, participants, () -> persist(runId));
            bind(run);
            persistenceSink.accept(run.snapshot());
            return run;
        } catch (RuntimeException ex) {
            runs.remove(runId);
            for (TowerParticipant participant : participants) runByPlayer.remove(participant.playerId(), runId);
            allocator.release(runId);
            throw ex;
        }
    }

    /** Restore one persisted run and reclaim its exact pre-restart instance slot. */
    public TowerRun restore(TowerRunSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (runs.containsKey(snapshot.runId())) throw new IllegalStateException("Tower run already exists: " + snapshot.runId());
        validateParticipantsAvailable(snapshot.participants());

        TowerInstanceSlot slot = allocator.reserve(snapshot.runId(), snapshot.slotIndex());
        try {
            TowerRun run = TowerRun.restore(snapshot, slot, () -> persist(snapshot.runId()));
            bind(run);
            return run;
        } catch (RuntimeException ex) {
            runs.remove(snapshot.runId());
            for (TowerParticipant participant : snapshot.participants()) {
                runByPlayer.remove(participant.playerId(), snapshot.runId());
            }
            allocator.release(snapshot.runId());
            throw ex;
        }
    }

    public Optional<TowerRun> get(UUID runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    public Optional<TowerRun> forPlayer(UUID playerId) {
        UUID runId = runByPlayer.get(playerId);
        return runId == null ? Optional.empty() : Optional.ofNullable(runs.get(runId));
    }

    public List<TowerRun> all() {
        return List.copyOf(runs.values());
    }

    public List<TowerRunSnapshot> snapshots() {
        return runs.values().stream().map(TowerRun::snapshot).toList();
    }

    /**
     * Removes runtime ownership only after callers have completed world/entity teardown.
     *
     * <p>The instance slot becomes reusable and persistent live-run data is removed only at this
     * explicit point, so a cell cannot be reassigned while its previous world state is still present.
     */
    public boolean release(UUID runId) {
        TowerRun removed = runs.remove(runId);
        if (removed == null) return false;
        for (TowerParticipant participant : removed.participants()) {
            runByPlayer.remove(participant.playerId(), runId);
        }
        allocator.release(runId);
        persistenceRemoveSink.accept(runId);
        return true;
    }

    public int activeRunCount() {
        return runs.size();
    }

    public void clearRuntimeState() {
        runs.clear();
        runByPlayer.clear();
        allocator.clear();
    }

    private void persist(UUID runId) {
        TowerRun run = runs.get(runId);
        if (run == null) return;
        if (run.state().terminal()) {
            // Keep the persisted live-run record until explicit release after teardown succeeds.
            return;
        }
        persistenceSink.accept(run.snapshot());
    }

    private void bind(TowerRun run) {
        runs.put(run.runId(), run);
        for (TowerParticipant participant : run.participants()) {
            UUID previous = runByPlayer.put(participant.playerId(), run.runId());
            if (previous != null) {
                // Defensive rollback: validateParticipantsAvailable should make this unreachable.
                runs.remove(run.runId());
                for (TowerParticipant rollback : run.participants()) {
                    runByPlayer.remove(rollback.playerId(), run.runId());
                }
                throw new IllegalStateException("Participant already belongs to Tower run " + previous);
            }
        }
    }

    private void validateParticipantsAvailable(Collection<TowerParticipant> participants) {
        List<UUID> seen = new ArrayList<>();
        for (TowerParticipant participant : participants) {
            Objects.requireNonNull(participant, "participants may not contain null");
            UUID playerId = participant.playerId();
            if (seen.contains(playerId)) throw new IllegalArgumentException("Duplicate participant: " + playerId);
            seen.add(playerId);
            UUID existingRun = runByPlayer.get(playerId);
            if (existingRun != null) {
                throw new IllegalStateException("Player " + playerId + " already belongs to Tower run " + existingRun);
            }
        }
    }
}
