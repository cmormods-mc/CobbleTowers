package com.cobbletowers.api.boss;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable Tower-facing terminal boss result. */
public record TowerBossResult(
        UUID encounterId,
        ResourceLocation definitionId,
        Outcome outcome,
        Set<UUID> participants,
        Set<UUID> activeParticipants,
        int elapsedCombatTicks,
        Map<UUID, Float> contribution
) {
    public enum Outcome { VICTORY, DEFEAT, ABORTED }

    public TowerBossResult {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(outcome, "outcome");
        participants = Set.copyOf(participants);
        activeParticipants = Set.copyOf(activeParticipants);
        contribution = Map.copyOf(contribution);
        if (!participants.containsAll(activeParticipants)) {
            throw new IllegalArgumentException("activeParticipants must be a subset of participants");
        }
        if (elapsedCombatTicks < 0) throw new IllegalArgumentException("elapsedCombatTicks must be >= 0");
    }
}
