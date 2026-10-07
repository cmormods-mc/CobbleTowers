package com.cobbletowers.definition;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * A contract a player can be handed (P32c): what to do, how many times and a small bounded reward. Drawn by date
 * ({@code ContractSchedule}); progress is per player. Conditions are a small vocabulary over the tower event stream.
 * @param period daily or weekly
 * @param reward CobbleDollars paid once on completion
 */
public record ContractTemplateDefinition(
        ResourceLocation id,
        int schemaVersion,
        String displayName,
        String description,
        Period period,
        Condition condition,
        int reward) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public enum Period { DAILY, WEEKLY }

    public enum Kind {
        FLOORS_CLEARED, BOSSES_DEFEATED, PURCHASES, SEVERE_DRAFTS, TRIALS_FINISHED, DAILY_TRIALS_FINISHED
    }

    /**
     * @param count how many times
     * @param maxSeconds clear the floor within this long (0 = no limit); FLOORS_CLEARED only
     * @param flawless no fainted Pokemon on that floor; FLOORS_CLEARED only
     * @param solo a solo run
     * @param minFloor at least this deep (0 = any), so cheap floors cannot be farmed
     */
    public record Condition(Kind kind, int count, int maxSeconds, boolean flawless, boolean solo, int minFloor) {
        public Condition {
            Objects.requireNonNull(kind, "kind");
            if (count < 1) throw new IllegalArgumentException("count must be >= 1, got " + count);
            if (maxSeconds < 0 || minFloor < 0) throw new IllegalArgumentException("limits must not be negative");
        }
    }

    public ContractTemplateDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(condition, "condition");
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("display_name must not be blank");
        description = description == null ? "" : description;
        if (reward < 0 || reward > 2000) throw new IllegalArgumentException("reward must be 0..2000, got " + reward);
    }

    public static ContractTemplateDefinition fromJson(ResourceLocation id, JsonObject root) {
        JsonObject condition = TowerJson.object(root, "condition");
        Kind kind;
        String rawKind = TowerJson.requireString(condition, "type").toUpperCase(Locale.ROOT);
        try {
            kind = Kind.valueOf(rawKind);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("condition type '" + rawKind.toLowerCase(Locale.ROOT) + "' is not known");
        }
        Period period;
        String rawPeriod = TowerJson.requireString(root, "period").toUpperCase(Locale.ROOT);
        try {
            period = Period.valueOf(rawPeriod);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("period must be daily or weekly, got " + rawPeriod.toLowerCase(Locale.ROOT));
        }
        return new ContractTemplateDefinition(id, TowerJson.requireInt(root, "schema_version"),
                TowerJson.requireString(root, "display_name"), TowerJson.string(root, "description", ""), period,
                new Condition(kind, TowerJson.requireInt(condition, "count"), TowerJson.integer(condition, "max_seconds", 0),
                        TowerJson.bool(condition, "flawless", false), TowerJson.bool(condition, "solo", false),
                        TowerJson.integer(condition, "min_floor", 0)),
                TowerJson.integer(root, "reward", 0));
    }
}
