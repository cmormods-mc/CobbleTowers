package com.cobbletowers.encounter;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * One opponent, as decided the moment a floor begins and unchanged afterwards.
 *
 * <p>Immutable on purpose (TDS #45): the level is taken once, so a party that faints, disconnects or
 * sends someone to spectate cannot lower the difficulty of the floor it is standing on.
 *
 * @param ordinal which opponent of the floor this is, from zero
 * @param aspects Cobblemon aspects, e.g. a regional form; empty for the base species
 */
public record EncounterSnapshot(int ordinal, ResourceLocation species, List<String> aspects, int level) {

    public EncounterSnapshot {
        Objects.requireNonNull(species, "species");
        aspects = List.copyOf(aspects);
        if (ordinal < 0) throw new IllegalArgumentException("ordinal must be >= 0, got " + ordinal);
        if (level < TowerLevelSnapshot.MIN_LEVEL || level > TowerLevelSnapshot.MAX_LEVEL) {
            throw new IllegalArgumentException("level " + level + " is outside 1..100");
        }
    }

    /** What Cobblemon's own property parser reads: "species level=n aspect=..". */
    public String toProperties() {
        StringBuilder properties = new StringBuilder(species.toString()).append(" level=").append(level);
        for (String aspect : aspects) properties.append(' ').append(aspect);
        return properties.toString();
    }
}
