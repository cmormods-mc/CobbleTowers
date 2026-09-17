package com.cobbletowers.definition;

import com.cobbletowers.api.rules.RulesetView;
import com.cobbletowers.encounter.TowerLevelSnapshot;
import com.google.gson.JsonObject;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * The rules a run is played under.
 *
 * <p>The level bounds live here and are applied by {@link TowerLevelSnapshot}, which is the only
 * place tower level maths exists (TDS #45, CONFIGURABLE: "do not scatter level math through
 * encounter code").
 */
public record RulesetDefinition(
        ResourceLocation id,
        int schemaVersion,
        int revision,
        int minEnemyLevel,
        int maxEnemyLevel,
        int registeredPartySize,
        boolean requiresBattleReadyParty,
        int itemActionBudget,
        boolean carriesHealthBetweenFloors,
        boolean carriesPpBetweenFloors) implements RulesetView {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    /** Six per player, four players: the 24 the TDS gates on measurement (#42). */
    public static final int MAX_REGISTERED_PARTY_SIZE = 6;

    public RulesetDefinition {
        Objects.requireNonNull(id, "id");
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1, got " + revision);
        if (minEnemyLevel < TowerLevelSnapshot.MIN_LEVEL || minEnemyLevel > TowerLevelSnapshot.MAX_LEVEL) {
            throw new IllegalArgumentException("min_enemy_level must be "
                    + TowerLevelSnapshot.MIN_LEVEL + ".." + TowerLevelSnapshot.MAX_LEVEL + ", got " + minEnemyLevel);
        }
        if (maxEnemyLevel < minEnemyLevel || maxEnemyLevel > TowerLevelSnapshot.MAX_LEVEL) {
            throw new IllegalArgumentException("max_enemy_level must be between min_enemy_level and "
                    + TowerLevelSnapshot.MAX_LEVEL + ", got " + maxEnemyLevel);
        }
        if (registeredPartySize < 1 || registeredPartySize > MAX_REGISTERED_PARTY_SIZE) {
            throw new IllegalArgumentException("registered_party_size must be 1.." + MAX_REGISTERED_PARTY_SIZE
                    + ", got " + registeredPartySize);
        }
        if (itemActionBudget < 0) {
            throw new IllegalArgumentException("item_action_budget must be >= 0, got " + itemActionBudget);
        }
    }

    public static RulesetDefinition fromJson(ResourceLocation id, JsonObject root) {
        JsonObject levels = TowerJson.object(root, "enemy_level");
        JsonObject carryover = TowerJson.object(root, "carryover");
        return new RulesetDefinition(
                id,
                TowerJson.requireInt(root, "schema_version"),
                TowerJson.integer(root, "revision", 1),
                TowerJson.integer(levels, "min", TowerLevelSnapshot.MIN_LEVEL),
                TowerJson.integer(levels, "max", TowerLevelSnapshot.MAX_LEVEL),
                TowerJson.integer(root, "registered_party_size", MAX_REGISTERED_PARTY_SIZE),
                TowerJson.bool(root, "requires_battle_ready_party", true),
                TowerJson.integer(root, "item_action_budget", 0),
                // On by default: the tower's own rule is that healing is bought, not given (TDS #16).
                TowerJson.bool(carryover, "health", true),
                TowerJson.bool(carryover, "pp", true));
    }
}
