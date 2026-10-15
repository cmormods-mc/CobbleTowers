package com.cobbletowers.definition;

import com.cobbletowers.definition.TrialPoolDefinition.Kind;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * What a rotating trial is composed from (P46): lists to draw each part from. The roll itself is
 * {@code RotatingRoll.of}, a pure function of this and the period.
 * @param floors trial length (5 daily, 10 weekly)
 * @param segmentSize floors that share one region
 * @param regions towers a segment may take its floors from
 * @param singleRegionPercent chance the whole trial is one region
 * @param playlists house rules to draw from
 * @param playlistPercent chance a trial has a house rule at all
 * @param levels enemy level locks to draw from
 * @param mutators forced modifiers to draw from (never relics); two of one group are never drawn together
 * @param mutatorCountWeights weight of 0, 1, 2... mutators
 * @param relics starting relics to draw from
 * @param relicPercent chance the party starts holding a relic
 * @param roomBias rooms that may be weighted up for the run
 * @param roomBiasPercent chance one is
 * @param weeklyRules rule ids a weekly draws one from (empty for a daily)
 * @param named hand-written entries for featured days
 * @param featuredEvery one period in this many uses a named entry; 0 disables
 */
public record RotatingRecipeDefinition(
        ResourceLocation id,
        int schemaVersion,
        String displayName,
        Kind kind,
        int floors,
        int streakMinFloors,
        int segmentSize,
        List<ResourceLocation> regions,
        int singleRegionPercent,
        List<ResourceLocation> playlists,
        int playlistPercent,
        List<Integer> levels,
        List<Mutator> mutators,
        List<Integer> mutatorCountWeights,
        List<ResourceLocation> relics,
        int relicPercent,
        List<String> roomBias,
        int roomBiasPercent,
        List<String> weeklyRules,
        List<TrialPoolDefinition.Entry> named,
        int featuredEvery) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /** A forced modifier; {@code group} keeps two weather-style mutators from being drawn together. */
    public record Mutator(ResourceLocation id, String group) {
        public Mutator {
            Objects.requireNonNull(id, "id");
            group = group == null || group.isBlank() ? id.toString() : group;
        }
    }

    public RotatingRecipeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        regions = List.copyOf(regions);
        playlists = List.copyOf(playlists);
        levels = List.copyOf(levels);
        mutators = List.copyOf(mutators);
        mutatorCountWeights = List.copyOf(mutatorCountWeights);
        relics = List.copyOf(relics);
        roomBias = List.copyOf(roomBias);
        weeklyRules = List.copyOf(weeklyRules);
        named = List.copyOf(named);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("display_name must not be blank");
        if (floors < 1 || floors > 10) throw new IllegalArgumentException("floors must be 1..10, got " + floors);
        if (streakMinFloors < 0 || streakMinFloors > floors) {
            throw new IllegalArgumentException("streak_min_floors must be 0..floors, got " + streakMinFloors);
        }
        if (segmentSize < 1 || segmentSize > floors) throw new IllegalArgumentException("segment_size must be 1..floors, got " + segmentSize);
        if (regions.isEmpty()) throw new IllegalArgumentException("a recipe needs at least one region");
        if (new HashSet<>(regions).size() != regions.size()) throw new IllegalArgumentException("regions has a duplicate");
        percent("single_region_percent", singleRegionPercent);
        percent("playlist_percent", playlistPercent);
        percent("relic_percent", relicPercent);
        percent("room_bias_percent", roomBiasPercent);
        if (playlistPercent > 0 && playlists.isEmpty()) throw new IllegalArgumentException("playlist_percent is set but playlists is empty");
        if (relicPercent > 0 && relics.isEmpty()) throw new IllegalArgumentException("relic_percent is set but relics is empty");
        if (roomBiasPercent > 0 && roomBias.isEmpty()) throw new IllegalArgumentException("room_bias_percent is set but room_bias is empty");
        if (levels.isEmpty()) throw new IllegalArgumentException("a recipe needs at least one level");
        for (int level : levels) {
            if (level < 1 || level > 100) throw new IllegalArgumentException("levels must be 1..100, got " + level);
        }
        if (mutatorCountWeights.isEmpty()) throw new IllegalArgumentException("mutator_count_weights needs at least one weight");
        int total = 0;
        for (int weight : mutatorCountWeights) {
            if (weight < 0) throw new IllegalArgumentException("mutator_count_weights must not be negative");
            total += weight;
        }
        if (total <= 0) throw new IllegalArgumentException("mutator_count_weights must not all be zero");
        Set<String> groups = new HashSet<>();
        for (Mutator mutator : mutators) groups.add(mutator.group());
        for (int count = 0; count < mutatorCountWeights.size(); count++) {
            if (mutatorCountWeights.get(count) > 0 && count > groups.size()) {
                throw new IllegalArgumentException("mutator_count_weights allows " + count + " mutators but the pool has only "
                        + groups.size() + " group(s)");
            }
        }
        if (kind == Kind.DAILY && !weeklyRules.isEmpty()) throw new IllegalArgumentException("weekly_rules belongs to a weekly recipe");
        if (featuredEvery < 0) throw new IllegalArgumentException("featured_every must not be negative");
        if (featuredEvery > 0 && named.isEmpty()) throw new IllegalArgumentException("featured_every is set but named is empty");
    }

    private static void percent(String key, int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(key + " must be 0..100, got " + value);
    }

    /** How many segments the trial has: floors split every {@code segmentSize}, the last one shorter if need be. */
    public int segments() {
        return (floors + segmentSize - 1) / segmentSize;
    }

    public static RotatingRecipeDefinition fromJson(ResourceLocation id, JsonObject root) {
        String kindName = TowerJson.requireString(root, "kind").toUpperCase(Locale.ROOT);
        Kind kind;
        try {
            kind = Kind.valueOf(kindName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("kind must be daily or weekly, got " + kindName.toLowerCase(Locale.ROOT));
        }
        List<Mutator> mutators = new ArrayList<>();
        JsonArray mutatorArray = root.has("mutators") && root.get("mutators").isJsonArray() ? root.getAsJsonArray("mutators") : new JsonArray();
        for (JsonElement element : mutatorArray) {
            if (element.isJsonObject()) {
                mutators.add(new Mutator(TowerJson.requireId(element.getAsJsonObject(), "id"), TowerJson.string(element.getAsJsonObject(), "group", "")));
            } else {
                mutators.add(new Mutator(ResourceLocation.parse(element.getAsString()), ""));
            }
        }
        List<Integer> levels = new ArrayList<>();
        if (root.has("levels")) for (JsonElement element : root.getAsJsonArray("levels")) levels.add(element.getAsInt());
        List<Integer> counts = new ArrayList<>();
        if (root.has("mutator_count_weights")) for (JsonElement element : root.getAsJsonArray("mutator_count_weights")) counts.add(element.getAsInt());
        if (counts.isEmpty()) counts.add(1);
        List<TrialPoolDefinition.Entry> named = new ArrayList<>();
        if (root.has("named")) {
            for (JsonElement element : root.getAsJsonArray("named")) {
                JsonObject entry = element.getAsJsonObject();
                named.add(new TrialPoolDefinition.Entry(TowerJson.requireId(entry, "tower"), TowerJson.optionalId(entry, "playlist"),
                        TowerJson.ids(entry, "modifiers"), TowerJson.integer(entry, "enemy_level", 0), TowerJson.string(entry, "label", "")));
            }
        }
        return new RotatingRecipeDefinition(id, TowerJson.requireInt(root, "schema_version"),
                TowerJson.requireString(root, "display_name"), kind, TowerJson.requireInt(root, "floors"),
                TowerJson.integer(root, "streak_min_floors", 0), TowerJson.integer(root, "segment_size", 2),
                TowerJson.requireIds(root, "regions"), TowerJson.integer(root, "single_region_percent", 0),
                TowerJson.ids(root, "playlists"), TowerJson.integer(root, "playlist_percent", 0), levels, mutators, counts,
                TowerJson.ids(root, "relics"), TowerJson.integer(root, "relic_percent", 0),
                TowerJson.strings(root, "room_bias"), TowerJson.integer(root, "room_bias_percent", 0),
                TowerJson.strings(root, "weekly_rules"), named, TowerJson.integer(root, "featured_every", 0));
    }
}
