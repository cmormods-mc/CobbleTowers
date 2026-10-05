package com.cobbletowers.encounter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;

/**
 * One opponent, as decided the moment a floor begins and unchanged afterwards.
 *
 * <p>Immutable on purpose (TDS #45): the level is taken once, so a party that faints, disconnects or
 * sends someone to spectate cannot lower the difficulty of the floor it is standing on.
 *
 * @param ordinal which opponent of the floor this is, from zero
 * @param aspects Cobblemon aspects, e.g. a regional form; empty for the base species
 * @param jerseyNumber the number on this opponent's jersey (TDS #67), empty for a non-jersey opponent
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
     * This slot filled by an Echo's Pokemon (P35): its own moves, ability, nature and item, at this slot's level. The species
     * stays the drawn one only for the record; what is built is {@code properties}.
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
     * The same string, optionally without the aspects.
     *
     * <p>The fallback {@code CobblemonBattleAdapter.spawn} retries with when Cobblemon does not
     * recognize an aspect (TDS #85): the base species is always a valid opponent, only the cosmetic
     * layer on top of it can fail.
     */
    public String toProperties(boolean includeAspects) {
        if (echoProperties.isPresent()) {
            // The fallback is the Echo's bare species: a held item or move the server no longer knows must not lose the floor.
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
