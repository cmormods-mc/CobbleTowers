package com.cobbletowers.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Which tactical facts a tower reveals about an upcoming opponent, and at what floor depth each one
 * starts concealing itself (TDS #22, #49).
 *
 * <p>Not a new source of gameplay data: every fact a category names (typing, threat level, field
 * conditions) already exists on {@code EncounterSnapshot} or {@code FloorDefinition} by the time this
 * is read. A profile only decides <em>when</em> a fact already sitting in this codebase is allowed to
 * reach a player -- the same reveal-threshold role P10's own design doc predicted for this phase.
 */
public record ScoutingProfileDefinition(
        ResourceLocation id,
        int schemaVersion,
        int revision,
        List<RevealCategory> categories) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * One tactical fact and the floor depth it starts hiding at.
     *
     * @param concealedFromFloor the first floor index this category is hidden on; negative means it
     *                           is never concealed, TDS #49's "observable information reveals
     *                           naturally" baseline
     */
    public record RevealCategory(String name, int concealedFromFloor) {
        public RevealCategory {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("a reveal category needs a name");
        }
    }

    public ScoutingProfileDefinition {
        Objects.requireNonNull(id, "id");
        categories = List.copyOf(categories);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1, got " + revision);
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("a scouting profile needs at least one reveal category");
        }
    }

    public static ScoutingProfileDefinition fromJson(ResourceLocation id, JsonObject root) {
        List<RevealCategory> categories = new ArrayList<>();
        if (root.has("categories")) {
            if (!root.get("categories").isJsonArray()) {
                throw new IllegalArgumentException("field 'categories' must be an array");
            }
            for (JsonElement element : root.getAsJsonArray("categories")) {
                JsonObject category = element.getAsJsonObject();
                categories.add(new RevealCategory(
                        TowerJson.requireString(category, "name"),
                        TowerJson.integer(category, "concealed_from_floor", -1)));
            }
        }
        return new ScoutingProfileDefinition(
                id,
                TowerJson.requireInt(root, "schema_version"),
                TowerJson.integer(root, "revision", 1),
                categories);
    }
}
