package com.cobbletowers.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;

/**
 * One file of the mastery track (P37), authored in {@code data/<namespace>/cobbletowers/mastery_tracks/*.json} or in the server owner's
 * {@code config/cobbletowers-tracks.json}. Files for the same tower (and the ones for every tower) merge into one track; see
 * {@code MasteryTrack.merge}. A level may be any number from 1 up, so a track can run past the 30 achievements the shipped towers have.
 *
 * <p>A level's {@code perks} <b>set</b> a perk's value from that level upward (they are rates applied where they are used, not claimed);
 * its {@code grants} and {@code cosmetics} are claimed by the player, in the season track's grant format.
 */
public record MasteryTrackDefinition(Optional<ResourceLocation> tower, List<Rank> ranks, List<Level> levels) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public record Rank(int level, String name) {
        public Rank {
            if (level < 1) throw new IllegalArgumentException("a rank's level must be >= 1, got " + level);
            if (name == null || name.isBlank()) throw new IllegalArgumentException("a rank needs a name");
        }
    }

    public record Level(int level, String label, OptionalInt vendorDiscountPercent, OptionalInt cobbleDollarBonusPercent,
                        OptionalInt raidPointsBonusPercent, List<SeasonTrackDefinition.Grant> grants, List<String> cosmetics) {
        public Level {
            if (level < 1) throw new IllegalArgumentException("a level must be >= 1, got " + level);
            label = label == null ? "" : label;
            grants = List.copyOf(grants);
            cosmetics = List.copyOf(cosmetics);
        }
    }

    public MasteryTrackDefinition {
        ranks = List.copyOf(ranks);
        levels = List.copyOf(levels);
    }

    public static MasteryTrackDefinition fromJson(JsonObject root) {
        int version = TowerJson.requireInt(root, "schema_version");
        if (version != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + version + " is not supported; expected " + SUPPORTED_SCHEMA_VERSION);
        }
        String towerText = TowerJson.string(root, "tower", "*");
        Optional<ResourceLocation> tower = towerText.equals("*") || towerText.isBlank() ? Optional.empty()
                : Optional.of(ResourceLocation.parse(towerText));
        List<Rank> ranks = new ArrayList<>();
        if (root.has("ranks")) {
            for (JsonElement element : root.getAsJsonArray("ranks")) {
                JsonObject rank = element.getAsJsonObject();
                ranks.add(new Rank(TowerJson.requireInt(rank, "level"), TowerJson.requireString(rank, "name")));
            }
        }
        List<Level> levels = new ArrayList<>();
        if (root.has("levels")) {
            for (JsonElement element : root.getAsJsonArray("levels")) levels.add(level(element.getAsJsonObject()));
        }
        return new MasteryTrackDefinition(tower, ranks, levels);
    }

    private static Level level(JsonObject json) {
        JsonObject perks = json.has("perks") ? json.getAsJsonObject("perks") : new JsonObject();
        return new Level(TowerJson.requireInt(json, "level"), TowerJson.string(json, "label", ""),
                percent(perks, "vendor_discount_percent"), percent(perks, "cobble_dollar_bonus_percent"),
                percent(perks, "raid_points_bonus_percent"), SeasonTrackDefinition.grants(json), TowerJson.strings(json, "cosmetics"));
    }

    private static OptionalInt percent(JsonObject perks, String key) {
        if (!perks.has(key)) return OptionalInt.empty();
        int value = perks.get(key).getAsInt();
        if (value < 0 || value > 100) throw new IllegalArgumentException(key + " must be 0..100, got " + value);
        return OptionalInt.of(value);
    }

    /** Whether this file applies to {@code towerId} (a file with no tower applies to every tower). */
    public boolean appliesTo(ResourceLocation towerId) {
        return tower.isEmpty() || tower.get().equals(towerId);
    }
}
