package com.cobbletowers.run;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable persistence boundary for one Tower run.
 *
 * <p>The snapshot contains IDs, primitive values and immutable value records only. It deliberately
 * excludes live players, worlds, battles, entities, callbacks and chunk tickets so it can survive
 * server restart without pinning runtime state.
 */
public record TowerRunSnapshot(
        UUID runId,
        long seed,
        int maxFloors,
        int slotIndex,
        int currentFloor,
        TowerRunState state,
        List<TowerParticipant> participants,
        int acceptedChallengeCount,
        boolean promotionPending,
        ResourceLocation pendingTemporary,
        List<ResourceLocation> recentAccepted,
        Map<ResourceLocation, Integer> permanentTiers
) {
    public TowerRunSnapshot {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(state, "state");
        participants = List.copyOf(participants);
        recentAccepted = List.copyOf(recentAccepted);
        permanentTiers = Map.copyOf(permanentTiers);
        if (maxFloors < 1) throw new IllegalArgumentException("maxFloors must be >= 1");
        if (slotIndex < 0) throw new IllegalArgumentException("slotIndex must be >= 0");
        if (currentFloor < 1 || currentFloor > maxFloors) throw new IllegalArgumentException("currentFloor outside run bounds");
        if (participants.isEmpty() || participants.size() > 4) throw new IllegalArgumentException("participants must contain 1..4 players");
        if (acceptedChallengeCount < 0) throw new IllegalArgumentException("acceptedChallengeCount must be >= 0");
        if (recentAccepted.size() > 5) throw new IllegalArgumentException("recentAccepted may contain at most five modifiers");
    }
}
