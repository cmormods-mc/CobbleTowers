package com.cobbletowers.definition;

import com.cobbletowers.api.tower.MilestoneKind;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A bespoke floor: floor 5's boss, floor 10's championship.
 *
 * <p>A {@link MilestoneKind#BOSS} milestone names the CobbleRaids definition its boss is built from,
 * which is how floor 5 reuses raid boss capability instead of duplicating it (TDS #52). Nothing here
 * calls CobbleRaids; the id is carried until P6 hands it to the encounter API.
 *
 * @param banksRewards whether clearing this floor banks the run's rewards (TDS #24)
 */
public record MilestoneDefinition(
        ResourceLocation id,
        int floorIndex,
        MilestoneKind kind,
        Optional<ResourceLocation> raidDefinitionId,
        boolean banksRewards) {

    public MilestoneDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(raidDefinitionId, "raidDefinitionId");
        if (floorIndex < 1) throw new IllegalArgumentException("floor must be >= 1, got " + floorIndex);
        if (raidDefinitionId.isEmpty()) {
            // Both kinds, not only BOSS. Every floor is finished by a CobbleRaids boss, and a
            // milestone floor takes the one named here rather than drawing from a pool -- so a
            // milestone without a definition is a floor nobody can complete. The definition
            // validator caught floor 10 in exactly that state the first time it ran.
            throw new IllegalArgumentException("a " + kind.name().toLowerCase(java.util.Locale.ROOT)
                    + " milestone must name a raid_definition; a milestone floor is finished by it");
        }
    }

    public static MilestoneDefinition fromJson(ResourceLocation id, JsonObject root) {
        return new MilestoneDefinition(
                id,
                TowerJson.requireInt(root, "floor"),
                parseKind(TowerJson.requireString(root, "kind")),
                TowerJson.optionalId(root, "raid_definition"),
                TowerJson.bool(root, "banks_rewards", true));
    }

    private static MilestoneKind parseKind(String raw) {
        try {
            return MilestoneKind.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("field 'kind' must be boss or champion, got '" + raw + "'");
        }
    }
}
