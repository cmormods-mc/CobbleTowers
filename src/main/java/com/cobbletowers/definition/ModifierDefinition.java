package com.cobbletowers.definition;

import com.cobbletowers.api.modifier.ModifierType;
import com.cobbletowers.api.modifier.ModifierView;
import com.cobbletowers.api.modifier.RiskTier;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One drafted challenge: what it changes, and what it cannot be held with (TDS #56, #58).
 *
 * <p>The effect payload is <b>one flat record with neutral defaults</b> rather than a payload class
 * per {@link ModifierType}. Five payload types, five parsers and a polymorphic dispatch would buy
 * nothing here: the fields are a handful of numbers and flags, and the GATE's simplicity rule warns
 * against exactly this kind of abstraction. What keeps it honest instead is {@link
 * #validateEffectMatchesType}, which refuses a definition whose effect has nothing to do with its
 * declared type -- so a REWARD modifier that quietly set a level offset is a content error at load,
 * not a surprise at floor 6.
 */
public record ModifierDefinition(
        ResourceLocation id,
        int schemaVersion,
        int revision,
        ModifierType type,
        String displayName,
        RiskTier risk,
        int weight,
        Optional<String> group,
        List<ResourceLocation> excludes,
        List<ResourceLocation> requires,
        int stackLimit,
        Effect effect) implements ModifierView {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * What a modifier actually does. Every field's default is "changes nothing".
     *
     * <p>Percentages rather than floating-point multipliers: 100 means unchanged, 125 means a
     * quarter more. Integers because these are persisted, compared and summed, and a stored double
     * that fails to round-trip would make two runs with identical drafts disagree.
     *
     * @param levelOffset       added to every ordinary opponent's level (ENEMY)
     * @param extraOpponents    additional opponents on the floor (ENCOUNTER)
     * @param bossLevelOffset   added to the floor boss's level (ENEMY)
     * @param bossHealthPercent the boss's shared pool, as a percentage of what it would be (ENEMY)
     * @param rewardPercent     what the floor's earnings are worth, recorded for P9 (REWARD)
     * @param bannedMoves       Showdown move ids the party may not use (PLAYER_CONSTRAINT, P8b)
     * @param allowSwitching    whether the party may switch (PLAYER_CONSTRAINT, P8b)
     * @param allowItems        whether the party may use items (PLAYER_CONSTRAINT, P8b)
     * @param weather           a Showdown weather id to set at battle start (FIELD, P8b)
     * @param terrain           a Showdown terrain id to set at battle start (FIELD, P8b)
     */
    public record Effect(
            int levelOffset,
            int extraOpponents,
            int bossLevelOffset,
            int bossHealthPercent,
            int rewardPercent,
            List<String> bannedMoves,
            boolean allowSwitching,
            boolean allowItems,
            Optional<String> weather,
            Optional<String> terrain) {

        /** Changes nothing: the baseline every field is measured against. */
        public static final Effect NEUTRAL =
                new Effect(0, 0, 0, 100, 100, List.of(), true, true, Optional.empty(), Optional.empty());

        public Effect {
            bannedMoves = List.copyOf(bannedMoves);
            Objects.requireNonNull(weather, "weather");
            Objects.requireNonNull(terrain, "terrain");
            if (bossHealthPercent < 1) {
                throw new IllegalArgumentException("boss_health_percent must be >= 1, got " + bossHealthPercent);
            }
            if (rewardPercent < 0) {
                throw new IllegalArgumentException("reward_percent must be >= 0, got " + rewardPercent);
            }
            if (extraOpponents < 0) {
                throw new IllegalArgumentException("extra_opponents must be >= 0, got " + extraOpponents);
            }
        }

        /** Whether this effect leaves the enemies exactly as they would have been. */
        public boolean touchesEnemies() {
            return levelOffset != 0 || bossLevelOffset != 0 || bossHealthPercent != 100;
        }

        public boolean touchesEncounter() {
            return extraOpponents != 0;
        }

        public boolean touchesReward() {
            return rewardPercent != 100;
        }

        public boolean touchesConstraints() {
            return !bannedMoves.isEmpty() || !allowSwitching || !allowItems;
        }

        public boolean touchesField() {
            return weather.isPresent() || terrain.isPresent();
        }

        public static Effect fromJson(JsonObject root) {
            if (root == null) return NEUTRAL;
            return new Effect(
                    TowerJson.integer(root, "level_offset", 0),
                    TowerJson.integer(root, "extra_opponents", 0),
                    TowerJson.integer(root, "boss_level_offset", 0),
                    TowerJson.integer(root, "boss_health_percent", 100),
                    TowerJson.integer(root, "reward_percent", 100),
                    TowerJson.strings(root, "banned_moves"),
                    TowerJson.bool(root, "allow_switching", true),
                    TowerJson.bool(root, "allow_items", true),
                    optionalString(root, "weather"),
                    optionalString(root, "terrain"));
        }

        private static Optional<String> optionalString(JsonObject root, String key) {
            String value = TowerJson.string(root, key, "");
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        }
    }

    public ModifierDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(risk, "risk");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(group, "group");
        excludes = List.copyOf(excludes);
        requires = List.copyOf(requires);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1, got " + revision);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display_name must not be blank");
        }
        if (weight < 1) throw new IllegalArgumentException("weight must be >= 1, got " + weight);
        if (stackLimit < 1) throw new IllegalArgumentException("stack_limit must be >= 1, got " + stackLimit);
        if (excludes.contains(id)) {
            throw new IllegalArgumentException(id + " excludes itself");
        }
        if (requires.contains(id)) {
            // Nothing could ever satisfy it: the modifier would have to be held before it can be
            // offered, and it can only be held by being offered.
            throw new IllegalArgumentException(id + " requires itself");
        }
        for (ResourceLocation required : requires) {
            if (excludes.contains(required)) {
                throw new IllegalArgumentException(id + " both requires and excludes " + required);
            }
        }
        validateEffectMatchesType(id, type, effect);
    }

    /**
     * Refuses a definition whose effect does not match what it says it is.
     *
     * <p>Cheap to write and it catches the mistake content actually makes: copying a modifier,
     * changing its type and forgetting to change the payload. Without this the file loads, the
     * modifier drafts, and it silently does nothing that its type is applied by.
     */
    private static void validateEffectMatchesType(ResourceLocation id, ModifierType type, Effect effect) {
        boolean matches = switch (type) {
            case ENEMY -> effect.touchesEnemies();
            case ENCOUNTER -> effect.touchesEncounter();
            case PLAYER_CONSTRAINT -> effect.touchesConstraints();
            case FIELD -> effect.touchesField();
            case REWARD -> effect.touchesReward();
        };
        if (!matches) {
            throw new IllegalArgumentException(id + " is type " + type
                    + " but its effect changes nothing that type applies");
        }
    }

    /**
     * Whether anything in this build would actually apply this modifier.
     *
     * <p>Asked of the <b>effect</b>, not of the type. The type-based version -- "everything except
     * PLAYER_CONSTRAINT and FIELD" -- read the same while those two were inert, and would have been
     * wrong for a case content will certainly write: an ENEMY modifier whose only effect is {@code
     * boss_health_percent}, which had nowhere to go until CobbleRaids grew a way to be asked for a
     * proportion of a pool it derives itself. A card like that passes a type check, is offered, is
     * voted on, and does nothing at all.
     *
     * <p>Since P8b every field has somewhere to go, so this is currently true of any effect that is
     * not neutral. It stays written this way regardless: the next field added will arrive before
     * whatever applies it, and this is the check that keeps it off the table until then.
     */
    public boolean effectiveNow() {
        return !effect.equals(Effect.NEUTRAL);
    }

    public static ModifierDefinition fromJson(ResourceLocation id, JsonObject root) {
        return new ModifierDefinition(
                id,
                TowerJson.requireInt(root, "schema_version"),
                TowerJson.integer(root, "revision", 1),
                parseEnum(ModifierType.class, TowerJson.requireString(root, "type"), "type"),
                TowerJson.requireString(root, "display_name"),
                parseEnum(RiskTier.class, TowerJson.string(root, "risk", "moderate"), "risk"),
                TowerJson.integer(root, "weight", 100),
                Optional.of(TowerJson.string(root, "group", "")).filter(value -> !value.isEmpty()),
                TowerJson.ids(root, "excludes"),
                TowerJson.ids(root, "requires"),
                TowerJson.integer(root, "stack_limit", 1),
                Effect.fromJson(TowerJson.object(root, "effect")));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String key) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new IllegalArgumentException("field '" + key + "' is not a known " + type.getSimpleName()
                    + ": " + raw);
        }
    }
}
