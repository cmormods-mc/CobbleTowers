package com.cobbletowers.definition;

import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One tower: which floors, in which order, under which rules.
 *
 * <p>Validated in the constructor, so an invalid tower cannot exist as an object -- the registry
 * catches the exception and skips the file. {@code schemaVersion} is the shape of this JSON and
 * {@code revision} is the author's own number for the content; a run records both, plus the digest
 * the registry computes, so "edited since this run started" is distinguishable from "renumbered"
 * (TDS #40).
 *
 * @param floorIds     every floor, in play order
 * @param milestoneIds milestone definitions this tower uses; their floor indices must exist
 * @param regionalTheme reserved for P10; parsed and carried, never resolved here
 * @param ascension    whether the tower continues past its last floor into harder cycles (P30) instead of ending there
 * @param scoutingProfile which reveal-threshold profile (P12) gates opponent info for this tower;
 *                        empty means everything reveals naturally (TDS #49's baseline)
 */
public record TowerDefinition(
        ResourceLocation id,
        String displayName,
        int schemaVersion,
        int revision,
        ResourceLocation rulesetId,
        ResourceLocation rewardTableId,
        List<ResourceLocation> floorIds,
        List<ResourceLocation> milestoneIds,
        Optional<ResourceLocation> regionalTheme,
        Optional<ResourceLocation> scoutingProfile,
        boolean ascension) {

    /** A tower that ends at its last floor, which is what every tower was before P30. */
    public TowerDefinition(ResourceLocation id, String displayName, int schemaVersion, int revision,
                           ResourceLocation rulesetId, ResourceLocation rewardTableId, List<ResourceLocation> floorIds,
                           List<ResourceLocation> milestoneIds, Optional<ResourceLocation> regionalTheme,
                           Optional<ResourceLocation> scoutingProfile) {
        this(id, displayName, schemaVersion, revision, rulesetId, rewardTableId, floorIds, milestoneIds, regionalTheme,
                scoutingProfile, false);
    }

    /** The only shape this build understands. A newer file is skipped with a message, not guessed at. */
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public TowerDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rulesetId, "rulesetId");
        Objects.requireNonNull(rewardTableId, "rewardTableId");
        Objects.requireNonNull(regionalTheme, "regionalTheme");
        Objects.requireNonNull(scoutingProfile, "scoutingProfile");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display_name must not be blank");
        }
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1, got " + revision);
        floorIds = List.copyOf(floorIds);
        milestoneIds = List.copyOf(milestoneIds);
        if (floorIds.isEmpty()) throw new IllegalArgumentException("a tower needs at least one floor");
        if (new HashSet<>(floorIds).size() != floorIds.size()) {
            throw new IllegalArgumentException("floors must not repeat: " + floorIds);
        }
        if (new HashSet<>(milestoneIds).size() != milestoneIds.size()) {
            throw new IllegalArgumentException("milestones must not repeat: " + milestoneIds);
        }
    }

    public int floorCount() {
        return floorIds.size();
    }

    /**
     * The floor of the tower a run floor is, for looking content up. Run floors past the last one are later cycles of an
     * ascending tower; for any other tower there is nothing past the last floor, so the number is returned as it is.
     */
    public int contentFloor(int runFloor) {
        return ascension ? com.cobbletowers.ascension.AscensionPolicy.towerFloorOf(runFloor, floorCount()) : runFloor;
    }

    /** Which cycle a run floor is in (0 is the base cycle); always 0 for a tower that does not ascend. */
    public int ascensionOf(int runFloor) {
        return ascension ? com.cobbletowers.ascension.AscensionPolicy.ascensionOf(runFloor, floorCount()) : 0;
    }

    public static TowerDefinition fromJson(ResourceLocation id, JsonObject root) {
        return new TowerDefinition(
                id,
                TowerJson.requireString(root, "display_name"),
                TowerJson.requireInt(root, "schema_version"),
                TowerJson.integer(root, "revision", 1),
                TowerJson.requireId(root, "ruleset"),
                TowerJson.requireId(root, "reward_table"),
                TowerJson.requireIds(root, "floors"),
                TowerJson.ids(root, "milestones"),
                TowerJson.optionalId(root, "regional_theme"),
                TowerJson.optionalId(root, "scouting_profile"),
                TowerJson.bool(root, "ascension", false));
    }
}
