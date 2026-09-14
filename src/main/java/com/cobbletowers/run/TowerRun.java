package com.cobbletowers.run;

import com.cobbletowers.instance.TowerInstanceSlot;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative aggregate for one Tower attempt.
 *
 * <p>This class owns run progression only. It deliberately does not place blocks, spawn Pokémon,
 * teleport players, or hold live entity/battle references. Those systems react to validated state
 * transitions and may therefore be restarted or replaced without corrupting persistent run data.
 * A lightweight mutation callback allows the owning manager to persist durable changes without this
 * domain object importing Minecraft's SavedData API.
 */
public final class TowerRun {
    private static final Runnable NO_OP = () -> {};

    private final UUID runId;
    private final long seed;
    private final int maxFloors;
    private final TowerInstanceSlot instanceSlot;
    private final Map<UUID, TowerParticipant> participants;
    private final TowerModifierProgress modifiers;
    private final Runnable onMutation;
    private int currentFloor;
    private TowerRunState state;

    public TowerRun(
            UUID runId,
            long seed,
            int maxFloors,
            TowerInstanceSlot instanceSlot,
            Collection<TowerParticipant> participants
    ) {
        this(runId, seed, maxFloors, instanceSlot, participants, 1, TowerRunState.PREPARING, null, NO_OP);
    }

    TowerRun(
            UUID runId,
            long seed,
            int maxFloors,
            TowerInstanceSlot instanceSlot,
            Collection<TowerParticipant> participants,
            Runnable onMutation
    ) {
        this(runId, seed, maxFloors, instanceSlot, participants, 1, TowerRunState.PREPARING, null, onMutation);
    }

    private TowerRun(
            UUID runId,
            long seed,
            int maxFloors,
            TowerInstanceSlot instanceSlot,
            Collection<TowerParticipant> participants,
            int currentFloor,
            TowerRunState state,
            TowerModifierProgress restoredModifiers,
            Runnable onMutation
    ) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.instanceSlot = Objects.requireNonNull(instanceSlot, "instanceSlot");
        this.state = Objects.requireNonNull(state, "state");
        this.onMutation = Objects.requireNonNull(onMutation, "onMutation");
        if (!runId.equals(instanceSlot.runId())) throw new IllegalArgumentException("instance slot belongs to another run");
        if (maxFloors < 1) throw new IllegalArgumentException("maxFloors must be >= 1");
        if (currentFloor < 1 || currentFloor > maxFloors) throw new IllegalArgumentException("currentFloor outside run bounds");
        this.seed = seed;
        this.maxFloors = maxFloors;
        this.currentFloor = currentFloor;
        this.modifiers = restoredModifiers == null ? new TowerModifierProgress(onMutation) : restoredModifiers;

        Objects.requireNonNull(participants, "participants");
        if (participants.isEmpty()) throw new IllegalArgumentException("Tower run requires at least one participant");
        if (participants.size() > 4) throw new IllegalArgumentException("Tower run supports at most four participants");
        this.participants = new LinkedHashMap<>();
        for (TowerParticipant participant : participants) {
            TowerParticipant previous = this.participants.put(participant.playerId(), Objects.requireNonNull(participant));
            if (previous != null) throw new IllegalArgumentException("Duplicate participant: " + participant.playerId());
        }
    }

    public UUID runId() { return runId; }
    public long seed() { return seed; }
    public int maxFloors() { return maxFloors; }
    public TowerInstanceSlot instanceSlot() { return instanceSlot; }
    public int currentFloor() { return currentFloor; }
    public TowerRunState state() { return state; }
    public TowerModifierProgress modifiers() { return modifiers; }
    public Collection<TowerParticipant> participants() { return ListView.copyOf(participants.values()); }

    public Set<UUID> activeParticipantIds() {
        return participants.values().stream()
                .filter(TowerParticipant::active)
                .map(TowerParticipant::playerId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public TowerParticipant participant(UUID playerId) {
        return participants.get(playerId);
    }

    public void activateFirstFloor() {
        requireState(TowerRunState.PREPARING);
        state = TowerRunState.FLOOR_ACTIVE;
        changed();
    }

    public void beginBossBattle() {
        requireState(TowerRunState.FLOOR_ACTIVE);
        state = TowerRunState.BOSS_BATTLE;
        changed();
    }

    /**
     * Converts an interrupted live battle back into a spawnable floor state.
     *
     * <p>No Showdown state is resumed. The battle adapter may now start the floor boss again at full
     * HP while Tower-level floor/modifier/reward state remains unchanged.
     */
    public void recoverInterruptedBossBattle() {
        requireState(TowerRunState.BOSS_BATTLE);
        state = TowerRunState.FLOOR_ACTIVE;
        changed();
    }

    public void beginUpgradeChoiceAfterBoss() {
        requireState(TowerRunState.BOSS_BATTLE);
        state = TowerRunState.CHOOSING_UPGRADE;
        changed();
    }

    /** Retained for non-boss test/dev transitions while every production floor still has a boss. */
    public void beginUpgradeChoice() {
        requireState(TowerRunState.FLOOR_ACTIVE);
        state = TowerRunState.CHOOSING_UPGRADE;
        changed();
    }

    public void finishUpgradeChoice() {
        requireState(TowerRunState.CHOOSING_UPGRADE);
        state = TowerRunState.READY_FOR_NEXT_FLOOR;
        changed();
    }

    /** Persist this intent before any next-floor structure/world mutation begins. */
    public void beginPreparingNextFloor() {
        requireState(TowerRunState.READY_FOR_NEXT_FLOOR);
        if (currentFloor >= maxFloors) throw new IllegalStateException("Final floor cannot advance");
        state = TowerRunState.PREPARING_NEXT_FLOOR;
        changed();
    }

    /** Call only after the next floor's required world preparation has completed successfully. */
    public void finishPreparingNextFloor() {
        requireState(TowerRunState.PREPARING_NEXT_FLOOR);
        currentFloor++;
        state = TowerRunState.FLOOR_ACTIVE;
        changed();
    }

    /** Legacy convenience transition for pure-state callers; world code should use prepare/finish. */
    public void advanceFloor() {
        beginPreparingNextFloor();
        finishPreparingNextFloor();
    }

    public void complete() {
        if (currentFloor != maxFloors) throw new IllegalStateException("Run may complete only on its final floor");
        if (state != TowerRunState.FLOOR_ACTIVE
                && state != TowerRunState.BOSS_BATTLE
                && state != TowerRunState.READY_FOR_NEXT_FLOOR) {
            throw new IllegalStateException("Run cannot complete from state " + state);
        }
        state = TowerRunState.COMPLETE;
        changed();
    }

    public boolean disconnect(UUID playerId) {
        if (state.terminal()) return false;
        TowerParticipant participant = participants.get(playerId);
        if (participant == null || !participant.active() || !participant.connected()) return false;
        participants.put(playerId, participant.disconnect());
        changed();
        return true;
    }

    public boolean reconnect(UUID playerId) {
        if (state.terminal()) return false;
        TowerParticipant participant = participants.get(playerId);
        if (participant == null || !participant.active() || participant.connected()) return false;
        participants.put(playerId, participant.reconnect());
        changed();
        return true;
    }

    /**
     * Decrements grace for disconnected active participants by one online server tick.
     *
     * @return true when persistent participant/run state changed.
     */
    public boolean tickReconnectGrace() {
        if (state.terminal()) return false;
        boolean changed = false;
        for (Map.Entry<UUID, TowerParticipant> entry : participants.entrySet()) {
            TowerParticipant participant = entry.getValue();
            if (!participant.active() || participant.connected()) continue;

            TowerParticipant next = participant.decrementReconnectGrace();
            if (next != participant) {
                entry.setValue(next);
                changed = true;
            }
            if (next.reconnectGraceExpired()) {
                entry.setValue(next.deactivate());
                changed = true;
            }
        }
        if (changed && activeParticipantIds().isEmpty()) state = TowerRunState.FAILED;
        if (changed) changed();
        return changed;
    }

    public boolean eliminate(UUID playerId) {
        if (state.terminal()) return false;
        TowerParticipant participant = participants.get(playerId);
        if (participant == null || !participant.active()) return false;
        participants.put(playerId, participant.deactivate());
        if (activeParticipantIds().isEmpty()) state = TowerRunState.FAILED;
        changed();
        return true;
    }

    public void abort() {
        if (!state.terminal()) {
            state = TowerRunState.ABORTED;
            changed();
        }
    }

    public static TowerRun restore(TowerRunSnapshot snapshot, TowerInstanceSlot instanceSlot) {
        return restore(snapshot, instanceSlot, NO_OP);
    }

    static TowerRun restore(TowerRunSnapshot snapshot, TowerInstanceSlot instanceSlot, Runnable onMutation) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(onMutation, "onMutation");
        TowerModifierProgress restoredModifiers = TowerModifierProgress.restore(
                snapshot.acceptedChallengeCount(),
                snapshot.promotionPending(),
                snapshot.pendingTemporary(),
                snapshot.recentAccepted(),
                snapshot.permanentTiers(),
                onMutation
        );
        return new TowerRun(
                snapshot.runId(),
                snapshot.seed(),
                snapshot.maxFloors(),
                instanceSlot,
                snapshot.participants(),
                snapshot.currentFloor(),
                snapshot.state(),
                restoredModifiers,
                onMutation
        );
    }

    public TowerRunSnapshot snapshot() {
        return new TowerRunSnapshot(
                runId,
                seed,
                maxFloors,
                instanceSlot.slotIndex(),
                currentFloor,
                state,
                ListView.copyOf(participants.values()),
                modifiers.acceptedChallengeCount(),
                modifiers.promotionPending(),
                modifiers.pendingTemporary(),
                modifiers.promotionChoicesForPersistence(),
                modifiers.permanentTiers()
        );
    }

    private void requireState(TowerRunState expected) {
        if (state != expected) throw new IllegalStateException("Expected run state " + expected + " but was " + state);
    }

    private void changed() {
        onMutation.run();
    }

    /** Tiny helper avoids exposing a mutable collection view while preserving participant order. */
    private static final class ListView {
        private ListView() {}
        static <T> java.util.List<T> copyOf(Collection<T> values) {
            return java.util.List.copyOf(values);
        }
    }
}
