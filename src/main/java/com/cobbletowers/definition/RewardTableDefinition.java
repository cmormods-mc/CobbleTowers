package com.cobbletowers.definition;

import com.cobbletowers.api.reward.RewardEntryView;
import com.cobbletowers.api.reward.RewardKind;
import com.cobbletowers.api.reward.RewardTableView;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * What a tower pays out, keyed by what earned it.
 *
 * <p>A tier absent from the JSON is an empty pool rather than a required one: unlike an encounter
 * pool, which always needs an opponent to draw, a table is not obliged to pay out on every kind of
 * ledger entry (a table with no boss tier simply never rolls one).
 *
 * <p>One table per tower (TowerDefinition.rewardTableId), not one per floor: what varies per floor is
 * depth, a number this table's own growth step scales by, not a distinct roster the way an encounter
 * pool's opponents are.
 */
public record RewardTableDefinition(
        ResourceLocation id,
        int schemaVersion,
        int revision,
        String displayName,
        int growthPercentPerFloor,
        Map<RewardKind, List<Entry>> tiers) implements RewardTableView {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * One possible item.
     *
     * @param minAmount the fewest this entry ever grants
     * @param maxAmount the most this entry ever grants, before growth and reward-percent scaling
     */
    public record Entry(ResourceLocation item, int minAmount, int maxAmount, int weight) implements RewardEntryView {
        public Entry {
            Objects.requireNonNull(item, "item");
            if (minAmount < 1) throw new IllegalArgumentException("min_amount must be >= 1, got " + minAmount);
            if (maxAmount < minAmount) {
                throw new IllegalArgumentException("max_amount must be >= min_amount, got " + maxAmount);
            }
            if (weight < 1) throw new IllegalArgumentException("weight must be >= 1, got " + weight);
        }
    }

    public RewardTableDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        tiers = Map.copyOf(tiers);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + schemaVersion + " is not supported; expected "
                    + SUPPORTED_SCHEMA_VERSION);
        }
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1, got " + revision);
        if (displayName.isBlank()) throw new IllegalArgumentException("display_name must not be blank");
        if (growthPercentPerFloor < 0) {
            throw new IllegalArgumentException("growth_percent_per_floor must be >= 0, got " + growthPercentPerFloor);
        }
    }

    @Override
    public List<RewardEntryView> tier(RewardKind kind) {
        return List.copyOf(tiers.getOrDefault(kind, List.of()));
    }

    /** The entries for one kind, as this record's own type rather than the view's. */
    public List<Entry> entriesFor(RewardKind kind) {
        return tiers.getOrDefault(kind, List.of());
    }

    /** Sum of one tier's weights; the denominator a draw divides by. */
    public int totalWeight(RewardKind kind) {
        int total = 0;
        for (Entry entry : entriesFor(kind)) total += entry.weight();
        return total;
    }

    public static RewardTableDefinition fromJson(ResourceLocation id, JsonObject root) {
        JsonObject tiersJson = TowerJson.object(root, "tiers");
        Map<RewardKind, List<Entry>> tiers = new EnumMap<>(RewardKind.class);
        for (RewardKind kind : RewardKind.values()) {
            List<Entry> entries = readTier(tiersJson, tierKey(kind));
            if (!entries.isEmpty()) tiers.put(kind, entries);
        }
        return new RewardTableDefinition(
                id,
                TowerJson.requireInt(root, "schema_version"),
                TowerJson.integer(root, "revision", 1),
                TowerJson.requireString(root, "display_name"),
                TowerJson.integer(root, "growth_percent_per_floor", 0),
                tiers);
    }

    private static List<Entry> readTier(JsonObject tiersJson, String key) {
        if (!tiersJson.has(key)) return List.of();
        if (!tiersJson.get(key).isJsonArray()) {
            throw new IllegalArgumentException("field 'tiers." + key + "' must be an array");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : tiersJson.getAsJsonArray(key)) {
            JsonObject entry = element.getAsJsonObject();
            entries.add(new Entry(
                    TowerJson.requireId(entry, "item"),
                    TowerJson.integer(entry, "min_amount", 1),
                    TowerJson.integer(entry, "max_amount", 1),
                    TowerJson.integer(entry, "weight", 100)));
        }
        return List.copyOf(entries);
    }

    private static String tierKey(RewardKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
