package com.cobbletowers.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One Pokemon the tower can lend (P33): a complete, fixed set, so everyone who drafts it has exactly the same Pokemon.
 *
 * <p>The fields are exactly what Cobblemon's own {@code PokemonProperties} understands (species, level, nature, ability, four
 * moves, a held item, IVs and EVs), so a set means what the game means by it. The rarity is the card's: it decides the excitement of
 * the reveal and where the draw places it, nothing else.
 *
 * @param species the Cobblemon species name, such as {@code garchomp}
 * @param evs     hp, atk, def, spa, spd, spe
 * @param item    a held item, if any, such as {@code cobblemon:life_orb}
 * @param role    a short description of what it does ("fast sweeper", "defensive pivot")
 */
public record RentalSetDefinition(
        ResourceLocation id,
        int schemaVersion,
        String species,
        int level,
        String ability,
        String nature,
        List<Integer> evs,
        List<Integer> ivs,
        Optional<ResourceLocation> item,
        List<String> moves,
        String role,
        Rarity rarity) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    /** Cobblemon refuses more than 510 EVs in total and 252 in one stat. */
    public static final int MAX_EVS_TOTAL = 510;
    public static final int MAX_EVS_PER_STAT = 252;

    /** CobblemonCards' rarity vocabulary, so a rental looks native next to a card from the collection. */
    public enum Rarity {
        COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC;

        public boolean atLeast(Rarity other) {
            return compareTo(other) >= 0;
        }

        /** Legendary and mythic Pokemon, which a team may keep only two of. */
        public boolean isTop() {
            return this == LEGENDARY || this == MYTHIC;
        }

        public String lower() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public RentalSetDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(rarity, "rarity");
        evs = List.copyOf(evs);
        ivs = List.copyOf(ivs);
        moves = List.copyOf(moves);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (species == null || species.isBlank()) throw new IllegalArgumentException("species must not be blank");
        if (level < 1 || level > 100) throw new IllegalArgumentException("level must be 1..100, got " + level);
        if (evs.size() != 6 || ivs.size() != 6) throw new IllegalArgumentException("evs and ivs need six numbers each");
        int total = 0;
        for (int ev : evs) {
            if (ev < 0 || ev > MAX_EVS_PER_STAT) throw new IllegalArgumentException("each ev must be 0.." + MAX_EVS_PER_STAT + ", got " + ev);
            total += ev;
        }
        if (total > MAX_EVS_TOTAL) throw new IllegalArgumentException("evs total " + total + " exceeds " + MAX_EVS_TOTAL);
        for (int iv : ivs) if (iv < 0 || iv > 31) throw new IllegalArgumentException("each iv must be 0..31, got " + iv);
        if (moves.isEmpty() || moves.size() > 4) throw new IllegalArgumentException("a set has one to four moves, got " + moves.size());
        if (ability == null || ability.isBlank()) throw new IllegalArgumentException("ability must not be blank");
        if (nature == null || nature.isBlank()) throw new IllegalArgumentException("nature must not be blank");
        role = role == null ? "" : role;
    }

    /** A readable name for the species: {@code garchomp} becomes {@code Garchomp}. */
    public String displayName() {
        return species.isEmpty() ? species : Character.toUpperCase(species.charAt(0)) + species.substring(1);
    }

    /** The property string Cobblemon parses to build this Pokemon, for example {@code garchomp level=50 nature=jolly ...}. */
    public String properties() {
        StringBuilder out = new StringBuilder(species).append(" level=").append(level).append(" nature=").append(nature)
                .append(" ability=").append(ability);
        out.append(" moves=").append(String.join(",", moves));
        item.ifPresent(held -> out.append(" held_item=").append(held));
        return out.toString();
    }

    public static RentalSetDefinition fromJson(ResourceLocation id, JsonObject root) {
        return new RentalSetDefinition(id, TowerJson.requireInt(root, "schema_version"), TowerJson.requireString(root, "species"),
                TowerJson.integer(root, "level", 50), TowerJson.requireString(root, "ability"),
                TowerJson.string(root, "nature", "hardy"), numbers(root, "evs", 0), numbers(root, "ivs", 31),
                TowerJson.optionalId(root, "item"), TowerJson.strings(root, "moves"), TowerJson.string(root, "role", ""),
                rarity(TowerJson.requireString(root, "rarity")));
    }

    private static Rarity rarity(String raw) {
        try {
            return Rarity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("rarity must be common, uncommon, rare, epic, legendary or mythic, got " + raw);
        }
    }

    /** Six numbers, or all {@code fallback} when the key is absent. */
    private static List<Integer> numbers(JsonObject root, String key, int fallback) {
        List<Integer> out = new ArrayList<>();
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            for (int i = 0; i < 6; i++) out.add(fallback);
            return out;
        }
        JsonArray array = root.getAsJsonArray(key);
        for (JsonElement element : array) out.add(element.getAsInt());
        return out;
    }
}
