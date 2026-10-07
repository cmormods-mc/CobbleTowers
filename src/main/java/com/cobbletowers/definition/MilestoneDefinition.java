package com.cobbletowers.definition;

import com.cobbletowers.api.tower.MilestoneKind;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A bespoke floor such as floor 5's boss or floor 10's championship. A {@link MilestoneKind#BOSS} names the
 * CobbleRaids definition it is built from (TDS #52).
 * @param banksRewards whether clearing it banks the run's rewards (TDS #24)
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
            // Both kinds need a definition: every floor ends with a CobbleRaids boss, so a milestone without one
            // cannot be completed.
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
