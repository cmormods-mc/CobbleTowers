package com.cobbletowers.definition;

import com.google.gson.JsonObject;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * One mastery achievement (P31): a name, a description and a condition. Unlocking it adds one mastery level in the tower it
 * was earned in. The same definitions apply to every tower; each tower tracks its own progress.
 *
 * <p>A condition is one of two shapes, both pure data:
 * <ul>
 *   <li>{@link Kind#CLEAR}: a <b>single cycle clear</b> meeting every constraint that is set
 *       ({@code min_ascension}, {@code flawless}, {@code solo}, {@code max_seconds}, {@code min_severe}, {@code min_score});</li>
 *   <li>{@link Kind#CYCLES_CLEARED} / {@link Kind#ASCENSION_REACHED}: a lifetime figure in the tower, {@code count} or
 *       {@code level} at least.</li>
 * </ul>
 * Unset constraints are neutral, so a file says only what it needs to.
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
     * @param kind          which shape
     * @param threshold     for the lifetime kinds, the figure to reach; unused (0) for {@link Kind#CLEAR}
     * @param minAscension  a clear must be at this Ascension or deeper
     * @param flawless      a clear must have no fainted player Pokemon
     * @param solo          a clear must be a run that started with one player
     * @param maxSeconds    a clear must take no more than this much active time (0 = no limit)
     * @param minSevere     a clear must hold at least this many severe modifiers
     * @param minScore      a clear must reach this difficulty score
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
