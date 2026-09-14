package com.cobbletowers.api.boss;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Opaque Tower-owned identity for one active boss encounter. */
public record TowerBossEncounter(UUID encounterId, ResourceLocation definitionId) {
    public TowerBossEncounter {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(definitionId, "definitionId");
    }
}
