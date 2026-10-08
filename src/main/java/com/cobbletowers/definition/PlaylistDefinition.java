package com.cobbletowers.definition;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.cobbletowers.encounter.TowerLevelSnapshot;
import net.minecraft.resources.ResourceLocation;

/**
 * A playlist (P32): the house rules a run is played under. It changes who may enter and what the run forbids, never
 * the tower. Standard is "no playlist". Each playlist has its own leaderboards.
 * @param enemyLevelMax enemy level ceiling, 0 for none
 * @param forcedModifiers modifiers every run starts holding
 * @param vendorClosed vendor refuses every purchase
 * @param maxPlayers team size cap, 0 for none
 * @param difficultyBonus points added to the difficulty score
 * @param rental whether the party is a drafted rental team (P33)
 * @param cardRewards CobblemonCards rewards for a completed run (P33b), or {@link CardRewards#NONE}
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
        int difficultyBonus,
        boolean rental,
        CardRewards cardRewards) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * CobblemonCards rewards for completing a run (P33b): one per Pokemon used, up to {@code maxRarity}, from at most
     * {@code runsPerDay} runs a day.
     */
    public record CardRewards(boolean enabled, RentalSetDefinition.Rarity maxRarity, int runsPerDay) {
        public static final CardRewards NONE = new CardRewards(false, RentalSetDefinition.Rarity.EPIC, 0);

        public CardRewards {
            if (runsPerDay < 0) throw new IllegalArgumentException("runs_per_day must not be negative");
            if (enabled && runsPerDay < 1) throw new IllegalArgumentException("card rewards need runs_per_day of at least 1");
        }

        static CardRewards fromJson(JsonObject root) {
            if (!root.has("card_rewards")) return NONE;
            JsonObject object = TowerJson.object(root, "card_rewards");
            RentalSetDefinition.Rarity max;
            String raw = TowerJson.string(object, "max_rarity", "epic");
            try {
                max = RentalSetDefinition.Rarity.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("card_rewards.max_rarity must be common, uncommon, rare, epic, legendary or mythic, got " + raw);
            }
            return new CardRewards(TowerJson.bool(object, "enabled", false), max, TowerJson.integer(object, "runs_per_day", 3));
        }
    }

    /**
     * What a party must satisfy; every clause is neutral when unset.
     * @param sameType shared type
     * @param maxLevel level cap, 0 for none
     * @param noFullyEvolved no fully evolved Pokemon
     * @param bannedLabels Cobblemon labels that may not appear
     * @param maxParty party size cap, 0 for the ruleset's own
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
     * The tower's ruleset narrowed by this playlist: the level ceiling can only lower the maximum and the party cap
     * only shrink the party.
     */
    public RulesetDefinition narrow(RulesetDefinition base) {
        int max = enemyLevelMax > 0 ? Math.max(base.minEnemyLevel(), Math.min(base.maxEnemyLevel(), enemyLevelMax)) : base.maxEnemyLevel();
        int size = party.maxParty() > 0 ? Math.min(base.registeredPartySize(), party.maxParty()) : base.registeredPartySize();
        if (max == base.maxEnemyLevel() && size == base.registeredPartySize()) return base;
        return base.withLevelsAndParty(base.minEnemyLevel(), max, size);
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
                TowerJson.integer(root, "difficulty_bonus", 0), TowerJson.bool(root, "rental", false), CardRewards.fromJson(root));
    }
}
