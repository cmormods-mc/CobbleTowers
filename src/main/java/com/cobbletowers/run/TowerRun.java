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
 */
public final class TowerRun {
    private final UUID runId;
    private final long seed;
    private final int maxFloors;
    private final TowerInstanceSlot instanceSlot;
    private final Map<UUID, TowerParticipant> participants;
    private final TowerModifierProgress modifiers;
    private int currentFloor;
    private TowerRunState state;

    public TowerRun(
            UUID runId,
            long seed,
            int maxFloors,
            TowerInstanceSlot instanceSlot,
            Collection<TowerParticipant> participants
    ) {
        this(runId, seed, maxFloors, instanceSlot, participants, 1, TowerRunState.PREPARING,
                new TowerModifierProgress());
    }

    private TowerRun(
            UUID runId,
            long seed,
            int maxFloors,
            TowerInstanceSlot instanceSlot,
            Collection<TowerParticipant> participants,
            int currentFloor,
            TowerRunState state,
            TowerModifierProgress modifiers
    ) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.instanceSlot = Objects.requireNonNull(instanceSlot, "instanceSlot");
        this.state = Objects.requireNonNull(state, "state");
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
        if (!runId.equals(instanceSlot.runId())) throw new IllegalArgumentException("instance slot belongs to another run");
        if (maxFloors < 1) throw new IllegalArgumentException("maxFloors must be >= 1");
        if (currentFloor < 1 || currentFloor > maxFloors) throw new IllegalArgumentException("currentFloor outside run bounds");
        this.seed = seed;
        this.maxFloors = maxFloors;
        this.currentFloor = currentFloor;

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
    }

    public void beginBossBattle() {
        requireState(TowerRunState.FLOOR_ACTIVE);
        state = TowerRunState.BOSS_BATTLE;
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
    }

    public void beginUpgradeChoiceAfterBoss() {
        requireState(TowerRunState.BOSS_BATTLE);
        state = TowerRunState.CHOOSING_UPGRADE;
    }

    /** Retained for non-boss test/dev transitions while every production floor still has a boss. */
    public void beginUpgradeChoice() {
        requireState(TowerRunState.FLOOR_ACTIVE);
        state = TowerRunState.CHOOSING_UPGRADE;
    }

    public void finishUpgradeChoice() {
        requireState(TowerRunState.CHOOSING_UPGRADE);
        state = TowerRunState.READY_FOR_NEXT_FLOOR;
    }

    /** Persist this intent before any next-floor structure/world mutation begins. */
    public void beginPreparingNextFloor() {
        requireState(TowerRunState.READY_FOR_NEXT_FLOOR);
        if (currentFloor >= maxFloors) throw new IllegalStateException("Final floor cannot advance");
        state = TowerRunState.PREPARING_NEXT_FLOOR;
    }

    /** Call only after the next floor's required world preparation has completed successfully. */
    public void finishPreparingNextFloor() {
        requireState(TowerRunState.PREPARING_NEXT_FLOOR);
        currentFloor++;
        state = TowerRunState.FLOOR_ACTIVE;
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
    }

    public boolean disconnect(UUID playerId) {
        if (state.terminal()) return false;
        TowerParticipant participant = participants.get(playerId);
        if (participant == null || !participant.active() || !participant.connected()) return false;
        participants.put(playerId, participant.disconnect());
        return true;
    }

    public boolean reconnect(UUID playerId) {
        if (state.terminal()) return false;
        TowerParticipant participant = participants.get(playerId);
        if (participant == null || !participant.active() || participant.connected()) return false;
        participants.put(playerId, participant.reconnect());
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
        return changed;
    }

    public boolean eliminate(UUID playerId) {
        if (state.terminal()) return false;
        TowerParticipant participant = participants.get(playerId);
        if (participant == null || !participant.active()) return false;
        participants.put(playerId, participant.deactivate());
        if (activeParticipantIds().isEmpty()) state = TowerRunState.FAILED;
        return true;
    }

    public void abort() {
        if (!state.terminal()) state = TowerRunState.ABORTED;
    }

    public static TowerRun restore(TowerRunSnapshot snapshot, TowerInstanceSlot instanceSlot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new TowerRun(
                snapshot.runId(),
                snapshot.seed(),
                snapshot.maxFloors(),
                instanceSlot,
                snapshot.participants(),
                snapshot.currentFloor(),
                snapshot.state(),
                TowerModifierProgress.restore(
                        snapshot.acceptedChallengeCount(),
                        snapshot.promotionPending(),
                        snapshot.pendingTemporary(),
                        snapshot.recentAccepted(),
                        snapshot.permanentTiers()
                )
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

    /** Tiny helper avoids exposing a mutable collection view while preserving participant order. */
    private static final class ListView {
        private ListView() {}
        static <T> java.util.List<T> copyOf(Collection<T> values) {
            return java.util.List.copyOf(values);
        }
    }
}
