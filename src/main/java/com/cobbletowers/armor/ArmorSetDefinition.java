package com.cobbletowers.armor;

import com.cobbletowers.definition.TowerJson;
import com.cobbletowers.showdown.TowerBattleFx;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * What an armor set is and does (P24): the pieces that make it up and the bonuses its worn pieces switch on.
 * Pure data: the items themselves are registered in code, because they must exist before any datapack is read.
 */
public record ArmorSetDefinition(
        ResourceLocation id,
        int schemaVersion,
        int revision,
        String displayName,
        Map<String, ResourceLocation> pieces,
        List<SetBonus> bonuses) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    public static final int MAX_BONUSES = 16;
    public static final List<String> SLOTS = List.of("head", "chest", "legs", "feet");
    private static final Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");

    public ArmorSetDefinition {
        Objects.requireNonNull(id, "id");
        pieces = Map.copyOf(pieces);
        bonuses = List.copyOf(bonuses);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1, got " + revision);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display_name must not be blank");
        }
        for (String slot : pieces.keySet()) {
            if (!SLOTS.contains(slot)) throw new IllegalArgumentException("unknown slot '" + slot + "'; expected " + SLOTS);
        }
        if (pieces.isEmpty()) throw new IllegalArgumentException("a set needs at least one piece");
        if (bonuses.size() > MAX_BONUSES) {
            throw new IllegalArgumentException("a set has at most " + MAX_BONUSES + " bonuses, got " + bonuses.size());
        }
    }

    /** The slot {@code item} fills in this set, if it is one of its pieces. */
    public Optional<String> slotOf(ResourceLocation item) {
        for (Map.Entry<String, ResourceLocation> piece : pieces.entrySet()) {
            if (piece.getValue().equals(item)) return Optional.of(piece.getKey());
        }
        return Optional.empty();
    }

    public static ArmorSetDefinition fromJson(ResourceLocation id, JsonObject root) {
        Map<String, ResourceLocation> pieces = new LinkedHashMap<>();
        JsonObject pieceObject = TowerJson.object(root, "pieces");
        for (String slot : pieceObject.keySet()) {
            pieces.put(slot, TowerJson.requireId(pieceObject, slot));
        }
        List<SetBonus> bonuses = new ArrayList<>();
        if (root.has("bonuses")) {
            if (!root.get("bonuses").isJsonArray()) throw new IllegalArgumentException("field 'bonuses' must be an array");
            for (JsonElement element : root.getAsJsonArray("bonuses")) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("every bonus must be an object");
                bonuses.add(parseBonus(element.getAsJsonObject()));
            }
        }
        return new ArmorSetDefinition(id,
                TowerJson.requireInt(root, "schema_version"),
                TowerJson.integer(root, "revision", 1),
                TowerJson.requireString(root, "display_name"),
                pieces, bonuses);
    }

    private static SetBonus parseBonus(JsonObject bonus) {
        int pieces = TowerJson.requireInt(bonus, "pieces");
        if (pieces < 1 || pieces > SLOTS.size()) {
            throw new IllegalArgumentException("bonus pieces must be 1.." + SLOTS.size() + ", got " + pieces);
        }
        String kind = TowerJson.requireString(bonus, "kind");
        switch (kind) {
            case "player" -> {
                String operation = TowerJson.string(bonus, "operation", "add_value");
                if (!OPERATIONS.contains(operation)) {
                    throw new IllegalArgumentException("unknown attribute operation '" + operation + "'");
                }
                if (!bonus.has("amount") || !bonus.get("amount").isJsonPrimitive()
                        || !bonus.get("amount").getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("a player bonus needs a numeric 'amount'");
                }
                double amount = bonus.get("amount").getAsDouble();
                if (!Double.isFinite(amount) || Math.abs(amount) > 100) {
                    throw new IllegalArgumentException("attribute amount must be within +-100, got " + amount);
                }
                return new SetBonus.PlayerAttribute(pieces, TowerJson.requireId(bonus, "attribute"), operation, amount);
            }
            case "cobblemon" -> {
                return new SetBonus.CobblemonModifier(pieces,
                        SetBonus.CobblemonModifier.Kind.of(TowerJson.requireString(bonus, "modifier")),
                        percent(bonus, 100));
            }
            case "tower" -> {
                SetBonus.TowerModifier.Kind modifier =
                        SetBonus.TowerModifier.Kind.of(TowerJson.requireString(bonus, "modifier"));
                return new SetBonus.TowerModifier(pieces, modifier, percent(bonus, modifier.max()));
            }
            case "battle" -> {
                if (!bonus.has("effects") || !bonus.get("effects").isJsonArray()) {
                    throw new IllegalArgumentException("a battle bonus needs an 'effects' array");
                }
                JsonArray effects = new JsonArray();
                for (JsonElement element : bonus.getAsJsonArray("effects")) {
                    // The same validation the operator seam and the Showdown handoff use: a set can never ask for
                    // something they would refuse. A bad operation rejects the whole set file, visibly, at load.
                    effects.add(TowerBattleFx.validate(element).orElseThrow(() ->
                            new IllegalArgumentException("battle effect is not a valid operation: " + element)));
                }
                if (effects.isEmpty()) throw new IllegalArgumentException("a battle bonus needs at least one effect");
                return new SetBonus.BattleEffects(pieces, effects);
            }
            default -> throw new IllegalArgumentException("unknown bonus kind '" + kind + "'");
        }
    }

    private static int percent(JsonObject bonus, int max) {
        int percent = TowerJson.requireInt(bonus, "percent");
        if (percent < 1 || percent > max) {
            throw new IllegalArgumentException("percent must be 1.." + max + ", got " + percent);
        }
        return percent;
    }
}
