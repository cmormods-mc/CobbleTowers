package com.cobbletowers.definition;

import com.cobbletowers.api.tower.FloorView;
import com.cobbletowers.api.tower.MilestoneKind;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One floor: which opponents it draws from, and any rules that differ from the tower's.
 *
 * <p>Implements {@link FloorView} directly rather than being copied into one: the record is already
 * immutable and its accessors are the view's methods, so a second type would only be a chance for
 * the two to disagree.
 *
 * @param rulesetOverride optional, for a floor that bends the tower's rules
 * @param modifierIds     reserved for P8; parsed and carried, never resolved here
 */
public record FloorDefinition(
        ResourceLocation id,
        int index,
        ResourceLocation encounterPoolId,
        Optional<MilestoneKind> milestone,
        Optional<ResourceLocation> rulesetOverride,
        List<ResourceLocation> modifierIds) implements FloorView {

    public FloorDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(encounterPoolId, "encounterPoolId");
        Objects.requireNonNull(milestone, "milestone");
        Objects.requireNonNull(rulesetOverride, "rulesetOverride");
        modifierIds = List.copyOf(modifierIds);
        if (index < 1) throw new IllegalArgumentException("index must be >= 1, got " + index);
    }

    public static FloorDefinition fromJson(ResourceLocation id, JsonObject root) {
        String milestone = TowerJson.string(root, "milestone", "").trim();
        return new FloorDefinition(
                id,
                TowerJson.requireInt(root, "index"),
                TowerJson.requireId(root, "encounter_pool"),
                milestone.isEmpty() ? Optional.empty() : Optional.of(parseMilestone(milestone)),
                TowerJson.optionalId(root, "ruleset_override"),
                TowerJson.ids(root, "modifiers"));
    }

    private static MilestoneKind parseMilestone(String raw) {
        try {
            return MilestoneKind.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("field 'milestone' must be boss or champion, got '" + raw + "'");
        }
    }
}
