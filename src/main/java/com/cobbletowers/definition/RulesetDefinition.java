package com.cobbletowers.definition;

import com.cobbletowers.api.rules.RulesetView;
import com.cobbletowers.encounter.TowerLevelSnapshot;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The rules a run is played under. Level bounds live here and are applied by {@link TowerLevelSnapshot}, the only
 * place tower level maths exists (TDS #45).
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
        boolean carriesPpBetweenFloors,
        boolean playerHeldItems,
        int enemyDrainStart,
        int enemyDrainEnd,
        List<String> floorStatuses) implements RulesetView {

    /** The statuses a floor may put on a lead (Showdown ids). */
    public static final Set<String> FLOOR_STATUSES = Set.of("psn", "brn", "par", "slp", "frz", "tox");
    /** Most an enemy may drain, as a percent of the damage it deals. */
    public static final int MAX_DRAIN_PERCENT = 50;

    /** A ruleset with no region rules (held items work, no drain, no floor status). */
    public RulesetDefinition(ResourceLocation id, int schemaVersion, int revision, int minEnemyLevel, int maxEnemyLevel,
                             int registeredPartySize, boolean requiresBattleReadyParty, int itemActionBudget,
                             boolean carriesHealthBetweenFloors, boolean carriesPpBetweenFloors) {
        this(id, schemaVersion, revision, minEnemyLevel, maxEnemyLevel, registeredPartySize, requiresBattleReadyParty,
                itemActionBudget, carriesHealthBetweenFloors, carriesPpBetweenFloors, true, 0, 0, List.of());
    }

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
        if (enemyDrainStart < 0 || enemyDrainStart > MAX_DRAIN_PERCENT || enemyDrainEnd < 0 || enemyDrainEnd > MAX_DRAIN_PERCENT) {
            throw new IllegalArgumentException("enemy_drain start and end must be 0.." + MAX_DRAIN_PERCENT
                    + ", got " + enemyDrainStart + " and " + enemyDrainEnd);
        }
        floorStatuses = List.copyOf(floorStatuses);
        for (String status : floorStatuses) {
            if (!FLOOR_STATUSES.contains(status)) {
                throw new IllegalArgumentException("floor_status '" + status + "' is not one of " + FLOOR_STATUSES);
            }
        }
    }

    /** The same rules with other enemy levels and party size (a trial's lock, a playlist's narrowing). */
    public RulesetDefinition withLevelsAndParty(int min, int max, int partySize) {
        return new RulesetDefinition(id, schemaVersion, revision, min, max, partySize, requiresBattleReadyParty,
                itemActionBudget, carriesHealthBetweenFloors, carriesPpBetweenFloors, playerHeldItems, enemyDrainStart,
                enemyDrainEnd, floorStatuses);
    }

    /**
     * The share of damage an enemy heals on floor {@code floor} of a tower of {@code floorCount}: {@code start} on the
     * first, {@code end} on the last, between them in a line; 0 when the ruleset has no drain.
     */
    public int enemyDrainPercent(int floor, int floorCount) {
        if (enemyDrainEnd <= 0 && enemyDrainStart <= 0) return 0;
        if (floorCount <= 1) return enemyDrainEnd;
        int clamped = Math.max(1, Math.min(floor, floorCount));
        return (int) Math.round(enemyDrainStart + (enemyDrainEnd - enemyDrainStart) * (clamped - 1) / (double) (floorCount - 1));
    }

    public static RulesetDefinition fromJson(ResourceLocation id, JsonObject root) {
        JsonObject levels = TowerJson.object(root, "enemy_level");
        JsonObject carryover = TowerJson.object(root, "carryover");
        JsonObject drain = TowerJson.object(root, "enemy_drain");
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
                TowerJson.bool(carryover, "pp", true),
                TowerJson.bool(root, "player_held_items", true),
                TowerJson.integer(drain, "start", 0),
                TowerJson.integer(drain, "end", 0),
                TowerJson.strings(root, "floor_status"));
    }
}
