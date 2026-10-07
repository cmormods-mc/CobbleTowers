package com.cobbletowers.definition;

import com.google.gson.JsonObject;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * One mastery achievement (P31): name, description and condition; unlocking it adds a mastery level in that tower. A
 * condition is either {@link Kind#CLEAR} (a single cycle clear meeting every set constraint) or a lifetime figure
 * ({@link Kind#CYCLES_CLEARED}, {@link Kind#ASCENSION_REACHED}). Unset constraints are neutral.
 */
public record AchievementDefinition(
        ResourceLocation id,
        int schemaVersion,
        String displayName,
        String description,
        Condition condition) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public enum Kind { CLEAR, CYCLES_CLEARED, ASCENSION_REACHED }

    /**
     * @param kind which shape
     * @param threshold the figure to reach for lifetime kinds, 0 for CLEAR
     * @param minAscension at this Ascension or deeper
     * @param flawless no fainted player Pokemon
     * @param solo started with one player
     * @param maxSeconds at most this much active time (0 = no limit)
     * @param minSevere at least this many severe modifiers
     * @param minScore at least this difficulty score
     */
    public record Condition(Kind kind, int threshold, int minAscension, boolean flawless, boolean solo, int maxSeconds,
                            int minSevere, int minScore) {

        public Condition {
            Objects.requireNonNull(kind, "kind");
            if (threshold < 0 || minAscension < 0 || maxSeconds < 0 || minSevere < 0 || minScore < 0) {
                throw new IllegalArgumentException("a condition's numbers must not be negative");
            }
            if (kind != Kind.CLEAR && threshold < 1) {
                throw new IllegalArgumentException(kind + " needs a count or level of at least 1");
            }
        }
    }

    public AchievementDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(condition, "condition");
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("display_name must not be blank");
        description = description == null ? "" : description;
    }

    public static AchievementDefinition fromJson(ResourceLocation id, JsonObject root) {
        JsonObject condition = TowerJson.object(root, "condition");
        String type = TowerJson.requireString(condition, "type");
        Condition parsed = switch (type) {
            case "clear" -> new Condition(Kind.CLEAR, 0,
                    TowerJson.integer(condition, "min_ascension", 0),
                    TowerJson.bool(condition, "flawless", false),
                    TowerJson.bool(condition, "solo", false),
                    TowerJson.integer(condition, "max_seconds", 0),
                    TowerJson.integer(condition, "min_severe", 0),
                    TowerJson.integer(condition, "min_score", 0));
            case "cycles_cleared" -> new Condition(Kind.CYCLES_CLEARED, TowerJson.requireInt(condition, "count"),
                    0, false, false, 0, 0, 0);
            case "ascension_reached" -> new Condition(Kind.ASCENSION_REACHED, TowerJson.requireInt(condition, "level"),
                    0, false, false, 0, 0, 0);
            default -> throw new IllegalArgumentException("condition type '" + type + "' is not known; expected clear,"
                    + " cycles_cleared or ascension_reached");
        };
        return new AchievementDefinition(id, TowerJson.requireInt(root, "schema_version"),
                TowerJson.requireString(root, "display_name"), TowerJson.string(root, "description", ""), parsed);
    }
}
