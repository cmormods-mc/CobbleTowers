package com.cobbletowers.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * The CobbleRaids bosses a floor may finish with, weighted. CobbleRaids offers no listing, so the tower names them.
 * Ids belong to the other mod and cannot be checked offline: only parsing and positive weights are validated; an
 * unknown id fails at boss start as a technical fault.
 */
public record BossPoolDefinition(
        ResourceLocation id,
        int schemaVersion,
        int revision,
        List<Entry> entries) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * One possible boss.
     * @param definition a CobbleRaids raid definition id, e.g. cobbleraids:lucario
     * @param levelOffset added to the floor's level
     */
    public record Entry(ResourceLocation definition, int weight, int levelOffset) {
        public Entry {
            Objects.requireNonNull(definition, "definition");
            if (weight < 1) throw new IllegalArgumentException("weight must be >= 1, got " + weight);
        }
    }

    public BossPoolDefinition {
        Objects.requireNonNull(id, "id");
        entries = List.copyOf(entries);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1, got " + revision);
        if (entries.isEmpty()) throw new IllegalArgumentException("a boss pool needs at least one entry");
    }

    /** Sum of every entry's weight; the denominator a draw divides by. */
    public int totalWeight() {
        int total = 0;
        for (Entry entry : entries) total += entry.weight();
        return total;
    }

    public static BossPoolDefinition fromJson(ResourceLocation id, JsonObject root) {
        if (!root.has("entries") || !root.get("entries").isJsonArray()) {
            throw new IllegalArgumentException("field 'entries' must be an array");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject();
            entries.add(new Entry(
                    TowerJson.requireId(entry, "definition"),
                    TowerJson.integer(entry, "weight", 100),
                    TowerJson.integer(entry, "level_offset", 0)));
        }
        return new BossPoolDefinition(
                id,
                TowerJson.requireInt(root, "schema_version"),
                TowerJson.integer(root, "revision", 1),
                entries);
    }
}
