package com.cobbletowers.definition;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.cobbletowers.encounter.TowerLevelSnapshot;
import net.minecraft.resources.ResourceLocation;

/**
 * A playlist (P32): the house rules a run is played under, chosen in the lobby. It changes <b>who may enter and what the run
 * forbids</b>, never the tower: the floors, bosses and economy are the same. Standard is simply "no playlist".
 *
 * <p>Everything is data: party clauses (checked at registration and again whenever a floor opens), an enemy level ceiling and a
 * party cap that narrow the tower's ruleset, modifiers forced onto the run, whether the vendor is closed, a player limit, and a
 * difficulty bonus added to the difficulty score so boards stay comparable. A playlist has its own leaderboards.
 *
 * @param enemyLevelMax    a ceiling on enemy levels, 0 for none
 * @param forcedModifiers  modifiers every run of this playlist starts holding
 * @param vendorClosed     whether the vendor refuses every purchase
 * @param maxPlayers       the most players a team may have, 0 for no limit
 * @param difficultyBonus  points added to the run's difficulty score
 */
public record PlaylistDefinition(
        ResourceLocation id,
        int schemaVersion,
        String displayName,
        String description,
        Clauses party,
        int enemyLevelMax,
        List<ResourceLocation> forcedModifiers,
        boolean vendorClosed,
        int maxPlayers,
        int difficultyBonus) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * What a party must satisfy. Every clause is neutral when unset.
     *
     * @param sameType         every registered Pokemon shares at least one type
     * @param maxLevel         no registered Pokemon above this level, 0 for none
     * @param noFullyEvolved   no Pokemon that cannot evolve further
     * @param bannedLabels     Cobblemon labels that may not appear (legendary, mythical, ultra_beast, paradox...)
     * @param maxParty         at most this many Pokemon are registered, 0 for the ruleset's own size
     */
    public record Clauses(boolean sameType, int maxLevel, boolean noFullyEvolved, Set<String> bannedLabels, int maxParty) {

        public static final Clauses NONE = new Clauses(false, 0, false, Set.of(), 0);

        public Clauses {
            bannedLabels = Set.copyOf(bannedLabels);
            if (maxLevel < 0 || maxParty < 0) throw new IllegalArgumentException("clause limits must not be negative");
        }

        public boolean any() {
            return sameType || maxLevel > 0 || noFullyEvolved || !bannedLabels.isEmpty();
        }
    }

    public PlaylistDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(party, "party");
        forcedModifiers = List.copyOf(forcedModifiers);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("display_name must not be blank");
        description = description == null ? "" : description;
        if (enemyLevelMax < 0 || enemyLevelMax > TowerLevelSnapshot.MAX_LEVEL) {
            throw new IllegalArgumentException("enemy_level_max must be 0.." + TowerLevelSnapshot.MAX_LEVEL + ", got " + enemyLevelMax);
        }
        if (maxPlayers < 0 || maxPlayers > 4) throw new IllegalArgumentException("max_players must be 0..4, got " + maxPlayers);
        if (difficultyBonus < 0) throw new IllegalArgumentException("difficulty_bonus must be >= 0, got " + difficultyBonus);
    }

    /**
     * The ruleset a run of this playlist is played under: the tower's, narrowed. The enemy level ceiling can only lower the
     * ruleset's maximum (never below its minimum), and the party cap can only shrink how many register.
     */
    public RulesetDefinition narrow(RulesetDefinition base) {
        int max = enemyLevelMax > 0 ? Math.max(base.minEnemyLevel(), Math.min(base.maxEnemyLevel(), enemyLevelMax)) : base.maxEnemyLevel();
        int size = party.maxParty() > 0 ? Math.min(base.registeredPartySize(), party.maxParty()) : base.registeredPartySize();
        if (max == base.maxEnemyLevel() && size == base.registeredPartySize()) return base;
        return new RulesetDefinition(base.id(), base.schemaVersion(), base.revision(), base.minEnemyLevel(), max, size,
                base.requiresBattleReadyParty(), base.itemActionBudget(), base.carriesHealthBetweenFloors(),
                base.carriesPpBetweenFloors());
    }

    public static PlaylistDefinition fromJson(ResourceLocation id, JsonObject root) {
        JsonObject party = TowerJson.object(root, "party");
        return new PlaylistDefinition(id, TowerJson.requireInt(root, "schema_version"),
                TowerJson.requireString(root, "display_name"), TowerJson.string(root, "description", ""),
                new Clauses(TowerJson.bool(party, "same_type", false), TowerJson.integer(party, "max_level", 0),
                        TowerJson.bool(party, "no_fully_evolved", false),
                        Set.copyOf(TowerJson.strings(party, "banned_labels")), TowerJson.integer(party, "max_party", 0)),
                TowerJson.integer(root, "enemy_level_max", 0), TowerJson.ids(root, "forced_modifiers"),
                TowerJson.bool(root, "vendor_closed", false), TowerJson.integer(root, "max_players", 0),
                TowerJson.integer(root, "difficulty_bonus", 0));
    }
}
