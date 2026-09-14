package com.cobbletowers.api.boss;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Immutable Tower-facing boss metadata independent of any boss implementation mod. */
public record TowerBossDefinition(
        ResourceLocation id,
        ResourceLocation species,
        String rarityTier,
        int level,
        long baseHealth,
        int maxPlayers
) {
    public TowerBossDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(rarityTier, "rarityTier");
        if (level < 1 || level > 100) throw new IllegalArgumentException("level must be 1..100");
        if (baseHealth < 1) throw new IllegalArgumentException("baseHealth must be >= 1");
        if (maxPlayers < 1 || maxPlayers > 4) throw new IllegalArgumentException("maxPlayers must be 1..4");
    }
}
