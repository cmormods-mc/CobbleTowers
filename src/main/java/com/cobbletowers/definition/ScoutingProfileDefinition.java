package com.cobbletowers.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Which facts a tower reveals about an upcoming opponent and the floor depth at which each starts hiding (TDS #22,
 * #49). Only decides when an existing fact reaches a player.
 */
public record ScoutingProfileDefinition(
        ResourceLocation id,
        int schemaVersion,
        int revision,
        List<RevealCategory> categories) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * One fact and the depth it starts hiding at.
     * @param concealedFromFloor first floor it is hidden on; negative means never (TDS #49)
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
