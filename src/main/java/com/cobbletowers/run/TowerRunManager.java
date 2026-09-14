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

/**
 * Server-thread owner of all active Tower runs.
 *
 * <p>Runs are indexed directly by run UUID and participant UUID. No global player/world scans are
 * needed to answer "which run owns this player?". Instance allocation and participant indexing are
 * created/restored/released atomically through this class.
 */
public final class TowerRunManager {
    private final TowerInstanceAllocator allocator;
    private final Map<UUID, TowerRun> runs = new HashMap<>();
    private final Map<UUID, UUID> runByPlayer = new HashMap<>();

    public TowerRunManager(TowerInstanceAllocator allocator) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
    }

    public TowerRun create(UUID runId, long seed, int maxFloors, Collection<TowerParticipant> participants) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(participants, "participants");
        if (runs.containsKey(runId)) throw new IllegalStateException("Tower run already exists: " + runId);
        validateParticipantsAvailable(participants);

        TowerInstanceSlot slot = allocator.allocate(runId);
        try {
            TowerRun run = new TowerRun(runId, seed, maxFloors, slot, participants);
            bind(run);
            return run;
        } catch (RuntimeException ex) {
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
            TowerRun run = TowerRun.restore(snapshot, slot);
            bind(run);
            return run;
        } catch (RuntimeException ex) {
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
     * <p>Keeping teardown outside this manager avoids a hidden world mutation in what is otherwise a
     * pure run-state service. The instance slot becomes reusable only at this explicit release point.
     */
    public boolean release(UUID runId) {
        TowerRun removed = runs.remove(runId);
        if (removed == null) return false;
        for (TowerParticipant participant : removed.participants()) {
            runByPlayer.remove(participant.playerId(), runId);
        }
        allocator.release(runId);
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
