package com.cobbletowers.encounter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;

/**
 * One opponent, decided when a floor begins and unchanged (TDS #45).
 * @param ordinal which opponent of the floor, from zero
 * @param aspects Cobblemon aspects, e.g. a regional form
 * @param jerseyNumber the jersey number (TDS #67), empty for a non-jersey opponent
 */
public record EncounterSnapshot(int ordinal, ResourceLocation species, List<String> aspects, int level,
                                 OptionalInt jerseyNumber, Optional<String> echoProperties, Optional<String> echoOwner) {

    public EncounterSnapshot {
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(jerseyNumber, "jerseyNumber");
        Objects.requireNonNull(echoProperties, "echoProperties");
        Objects.requireNonNull(echoOwner, "echoOwner");
        aspects = List.copyOf(aspects);
        if (ordinal < 0) throw new IllegalArgumentException("ordinal must be >= 0, got " + ordinal);
        if (level < TowerLevelSnapshot.MIN_LEVEL || level > TowerLevelSnapshot.MAX_LEVEL) {
            throw new IllegalArgumentException("level " + level + " is outside 1..100");
        }
    }

    /** An opponent that is not an Echo: everything before P35. */
    public EncounterSnapshot(int ordinal, ResourceLocation species, List<String> aspects, int level,
                             OptionalInt jerseyNumber) {
        this(ordinal, species, aspects, level, jerseyNumber, Optional.empty(), Optional.empty());
    }

    /**
     * This slot filled by an Echo's Pokemon (P35): its own moves, ability, nature and item at this slot's level. What
     * is built is {@code properties}.
     */
    public EncounterSnapshot withEcho(String properties, String owner) {
        return new EncounterSnapshot(ordinal, species, aspects, level, OptionalInt.empty(),
                Optional.of(properties), Optional.of(owner));
    }

    /** A non-jersey opponent: every encounter before P11, and every one outside a regional theme's five. */
    public EncounterSnapshot(int ordinal, ResourceLocation species, List<String> aspects, int level) {
        this(ordinal, species, aspects, level, OptionalInt.empty());
    }

    /** What Cobblemon's own property parser reads: "species level=n aspect=..". */
    public String toProperties() {
        return toProperties(true);
    }

    /**
     * The same string, optionally without aspects: the fallback when Cobblemon does not recognize an aspect (TDS
     * #85).
     */
    public String toProperties(boolean includeAspects) {
        if (echoProperties.isPresent()) {
            // The fallback is the Echo's bare species: a held item or move the server no longer knows must not lose
            // the floor.
            String full = com.cobbletowers.echo.EchoPolicy.atLevel(echoProperties.get(), level);
            return includeAspects ? full : com.cobbletowers.echo.EchoPolicy.speciesOf(full) + " level=" + level;
        }
        StringBuilder properties = new StringBuilder(species.toString()).append(" level=").append(level);
        if (includeAspects) {
            for (String aspect : aspects) properties.append(' ').append(aspect);
        }
        return properties.toString();
    }
}
