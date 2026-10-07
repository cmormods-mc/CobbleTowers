package com.cobbletowers.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The opponents a floor can draw, weighted so a themed tower can make its signature Pokemon common (TDS #73). Drawing
 * is {@code EncounterDraw}'s job, from the run seed (TDS #29).
 * @param regionalPool a regional theme id; parsed and carried, resolved elsewhere
 */
public record EncounterPoolDefinition(
        ResourceLocation id,
        int schemaVersion,
        int revision,
        List<Entry> entries,
        Optional<ResourceLocation> regionalPool) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * One possible opponent.
     * @param aspects Cobblemon aspects, e.g. a regional form; empty for the base species
     * @param levelOffset added to the level snapshot
     */
    public record Entry(ResourceLocation species, List<String> aspects, int weight, int levelOffset) {
        public Entry {
            Objects.requireNonNull(species, "species");
            aspects = List.copyOf(aspects);
            if (weight < 1) throw new IllegalArgumentException("weight must be >= 1, got " + weight);
        }
    }

    public EncounterPoolDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(regionalPool, "regionalPool");
        entries = List.copyOf(entries);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1, got " + revision);
        if (entries.isEmpty()) throw new IllegalArgumentException("an encounter pool needs at least one entry");
    }

    /** Sum of every entry's weight; the denominator a draw divides by. */
    public int totalWeight() {
        int total = 0;
        for (Entry entry : entries) total += entry.weight();
        return total;
    }

    public static EncounterPoolDefinition fromJson(ResourceLocation id, JsonObject root) {
        if (!root.has("entries") || !root.get("entries").isJsonArray()) {
            throw new IllegalArgumentException("field 'entries' must be an array");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject();
            entries.add(new Entry(
                    TowerJson.requireId(entry, "species"),
                    TowerJson.strings(entry, "aspects"),
                    TowerJson.integer(entry, "weight", 100),
                    TowerJson.integer(entry, "level_offset", 0)));
        }
        return new EncounterPoolDefinition(
                id,
                TowerJson.requireInt(root, "schema_version"),
                TowerJson.integer(root, "revision", 1),
                entries,
                TowerJson.optionalId(root, "regional_pool"));
    }
}
