package com.cobbletowers.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The candidates a kind of trial draws its day from (P32). A pool is data: which towers, playlists and mutator modifiers a trial
 * may combine, how many floors it lasts, and how many floors a daily attempt must clear to count for the streak. The trial for a
 * given day is a pure function of the date and the pool ({@code TrialSchedule}), so every server agrees without talking.
 *
 * @param floors          how many floors the trial lasts (5 for the daily, 10 for the weekly)
 * @param streakMinFloors floors a daily attempt must clear to keep the streak going
 */
public record TrialPoolDefinition(
        ResourceLocation id,
        int schemaVersion,
        String displayName,
        Kind kind,
        int floors,
        int streakMinFloors,
        List<Entry> entries) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public enum Kind { DAILY, WEEKLY }

    /**
     * One candidate trial.
     *
     * @param playlist   the playlist's house rules, if any
     * @param modifiers  mutators the run starts holding (a field condition, an enemy tweak)
     * @param enemyLevel every enemy is exactly this level, so results compare; 0 for no lock
     * @param label      a name for the board and the announcement
     */
    public record Entry(ResourceLocation tower, Optional<ResourceLocation> playlist, List<ResourceLocation> modifiers,
                        int enemyLevel, String label) {
        public Entry {
            Objects.requireNonNull(tower, "tower");
            Objects.requireNonNull(playlist, "playlist");
            modifiers = List.copyOf(modifiers);
            if (enemyLevel < 0 || enemyLevel > 100) throw new IllegalArgumentException("enemy_level must be 0..100, got " + enemyLevel);
            label = label == null ? "" : label;
        }
    }

    public TrialPoolDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        entries = List.copyOf(entries);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("display_name must not be blank");
        if (floors < 1 || floors > 10) throw new IllegalArgumentException("floors must be 1..10, got " + floors);
        if (streakMinFloors < 0 || streakMinFloors > floors) {
            throw new IllegalArgumentException("streak_min_floors must be 0..floors, got " + streakMinFloors);
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("a trial pool needs at least one entry");
    }

    public static TrialPoolDefinition fromJson(ResourceLocation id, JsonObject root) {
        String kind = TowerJson.requireString(root, "kind").toUpperCase(java.util.Locale.ROOT);
        Kind parsed;
        try {
            parsed = Kind.valueOf(kind);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("kind must be daily or weekly, got " + kind.toLowerCase(java.util.Locale.ROOT));
        }
        List<Entry> entries = new ArrayList<>();
        JsonArray array = root.has("entries") && root.get("entries").isJsonArray() ? root.getAsJsonArray("entries") : new JsonArray();
        for (JsonElement element : array) {
            JsonObject entry = element.getAsJsonObject();
            entries.add(new Entry(TowerJson.requireId(entry, "tower"), TowerJson.optionalId(entry, "playlist"),
                    TowerJson.ids(entry, "modifiers"), TowerJson.integer(entry, "enemy_level", 0),
                    TowerJson.string(entry, "label", "")));
        }
        return new TrialPoolDefinition(id, TowerJson.requireInt(root, "schema_version"),
                TowerJson.requireString(root, "display_name"), parsed, TowerJson.requireInt(root, "floors"),
                TowerJson.integer(root, "streak_min_floors", 0), entries);
    }
}
