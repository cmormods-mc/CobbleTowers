package com.cobbletowers.definition;

import com.cobbletowers.api.reward.RewardEntryView;
import com.cobbletowers.api.reward.RewardKind;
import com.cobbletowers.api.reward.RewardTableView;
import com.cobbletowers.api.tower.MilestoneKind;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * What a tower pays out, by ledger kind. An absent tier is an empty pool. One table per tower; depth scales it
 * through the growth step.
 */
public record RewardTableDefinition(
        ResourceLocation id,
        int schemaVersion,
        int revision,
        String displayName,
        int growthPercentPerFloor,
        Map<RewardKind, List<Entry>> tiers,
        Map<MilestoneKind, MilestoneReward> milestones,
        Map<RewardKind, Integer> rolls) implements RewardTableView {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    /** Most rolls one ledger entry may make (a table asking for more is refused, not clamped). */
    public static final int MAX_ROLLS = 5;

    /**
     * One possible item.
     * @param minAmount fewest granted
     * @param maxAmount most granted, before growth and reward-percent
     * @param minFloor first floor of the tower (within its cycle) this can be rolled on
     * @param maxFloor last floor of the tower this can be rolled on
     */
    public record Entry(ResourceLocation item, int minAmount, int maxAmount, int weight, int minFloor, int maxFloor)
            implements RewardEntryView {

        /** An entry that can be rolled on every floor. */
        public Entry(ResourceLocation item, int minAmount, int maxAmount, int weight) {
            this(item, minAmount, maxAmount, weight, 1, Integer.MAX_VALUE);
        }

        /** Whether this can be rolled on {@code floor} (the floor within the tower's own cycle). */
        public boolean availableOn(int floor) {
            return floor >= minFloor && floor <= maxFloor;
        }

        public Entry {
            Objects.requireNonNull(item, "item");
            if (minAmount < 1) throw new IllegalArgumentException("min_amount must be >= 1, got " + minAmount);
            if (maxAmount < minAmount) {
                throw new IllegalArgumentException("max_amount must be >= min_amount, got " + maxAmount);
            }
            if (weight < 1) throw new IllegalArgumentException("weight must be >= 1, got " + weight);
            if (minFloor < 1 || maxFloor < minFloor) {
                throw new IllegalArgumentException("min_floor and max_floor must satisfy 1 <= min_floor <= max_floor, got "
                        + minFloor + " and " + maxFloor);
            }
        }
    }

    /** One item a milestone always pays, and how many. Every participant gets this amount in full. */
    public record Guaranteed(ResourceLocation item, int amount) {
        public Guaranteed {
            Objects.requireNonNull(item, "item");
            if (amount < 1) throw new IllegalArgumentException("amount must be >= 1, got " + amount);
        }
    }

    /**
     * What clearing a milestone floor pays beyond the ordinary tiers (P21).
     * @param guaranteed paid to every participant in full, never scaled
     * @param bonusRolls extra weighted picks from {@code bonusPool}
     */
    public record MilestoneReward(List<Guaranteed> guaranteed, int bonusRolls, List<Entry> bonusPool) {
        public MilestoneReward {
            guaranteed = List.copyOf(guaranteed);
            bonusPool = List.copyOf(bonusPool);
            if (bonusRolls < 0) throw new IllegalArgumentException("bonus_rolls must be >= 0, got " + bonusRolls);
            if (bonusRolls > 0 && bonusPool.isEmpty()) {
                throw new IllegalArgumentException("bonus_rolls is " + bonusRolls + " but bonus_pool is empty");
            }
            if (guaranteed.isEmpty() && bonusRolls == 0) {
                throw new IllegalArgumentException("a milestone reward needs something to pay: guaranteed or bonus_rolls");
            }
        }
    }

    /** A table with no milestone section, which is every table written before P21. */
    public RewardTableDefinition(ResourceLocation id, int schemaVersion, int revision, String displayName,
                                 int growthPercentPerFloor, Map<RewardKind, List<Entry>> tiers) {
        this(id, schemaVersion, revision, displayName, growthPercentPerFloor, tiers, Map.of(), Map.of());
    }

    /** A table that rolls once per ledger entry, which is every table written before P39. */
    public RewardTableDefinition(ResourceLocation id, int schemaVersion, int revision, String displayName,
                                 int growthPercentPerFloor, Map<RewardKind, List<Entry>> tiers,
                                 Map<MilestoneKind, MilestoneReward> milestones) {
        this(id, schemaVersion, revision, displayName, growthPercentPerFloor, tiers, milestones, Map.of());
    }

    public RewardTableDefinition {
        Objects.requireNonNull(id, "id");
        milestones = Map.copyOf(milestones);
        rolls = Map.copyOf(rolls);
        for (Map.Entry<RewardKind, Integer> roll : rolls.entrySet()) {
            if (roll.getValue() < 1 || roll.getValue() > MAX_ROLLS) {
                throw new IllegalArgumentException("rolls for " + roll.getKey() + " must be 1.." + MAX_ROLLS + ", got " + roll.getValue());
            }
        }
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

    /** How many items one ledger entry of this kind rolls: 1 unless the table says more. */
    public int rollsFor(RewardKind kind) {
        return rolls.getOrDefault(kind, 1);
    }

    /**
     * The entries of one kind that can be rolled on {@code floor}. When the floor band leaves nothing, the whole pool,
     * so a table can never pay nothing for a mistake in its bands.
     */
    public List<Entry> entriesFor(RewardKind kind, int floor) {
        List<Entry> all = entriesFor(kind);
        List<Entry> here = new ArrayList<>();
        for (Entry entry : all) if (entry.availableOn(floor)) here.add(entry);
        return here.isEmpty() ? all : here;
    }

    /** What a milestone of this kind pays, if the table says. */
    public Optional<MilestoneReward> milestoneReward(MilestoneKind kind) {
        return Optional.ofNullable(milestones.get(kind));
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
                tiers,
                readMilestones(root),
                readRolls(root));
    }

    private static Map<RewardKind, Integer> readRolls(JsonObject root) {
        JsonObject section = TowerJson.object(root, "rolls");
        Map<RewardKind, Integer> out = new EnumMap<>(RewardKind.class);
        for (RewardKind kind : RewardKind.values()) {
            if (section.has(tierKey(kind))) out.put(kind, section.get(tierKey(kind)).getAsInt());
        }
        return out;
    }

    private static Map<MilestoneKind, MilestoneReward> readMilestones(JsonObject root) {
        if (!root.has("milestones")) return Map.of();
        JsonObject section = TowerJson.object(root, "milestones");
        Map<MilestoneKind, MilestoneReward> out = new EnumMap<>(MilestoneKind.class);
        for (String key : section.keySet()) {
            MilestoneKind kind;
            try {
                kind = MilestoneKind.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("milestones." + key + " is not a milestone kind (boss or champion)");
            }
            JsonObject body = section.getAsJsonObject(key);
            List<Guaranteed> guaranteed = new ArrayList<>();
            if (body.has("guaranteed")) {
                for (JsonElement element : body.getAsJsonArray("guaranteed")) {
                    JsonObject entry = element.getAsJsonObject();
                    guaranteed.add(new Guaranteed(TowerJson.requireId(entry, "item"), TowerJson.integer(entry, "amount", 1)));
                }
            }
            out.put(kind, new MilestoneReward(guaranteed, TowerJson.integer(body, "bonus_rolls", 0),
                    body.has("bonus_pool") ? readEntries(body.getAsJsonArray("bonus_pool")) : List.of()));
        }
        return out;
    }

    private static List<Entry> readEntries(com.google.gson.JsonArray array) {
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject entry = element.getAsJsonObject();
            entries.add(new Entry(
                    TowerJson.requireId(entry, "item"),
                    TowerJson.integer(entry, "min_amount", 1),
                    TowerJson.integer(entry, "max_amount", 1),
                    TowerJson.integer(entry, "weight", 100),
                    TowerJson.integer(entry, "min_floor", 1),
                    TowerJson.integer(entry, "max_floor", Integer.MAX_VALUE)));
        }
        return List.copyOf(entries);
    }

    private static List<Entry> readTier(JsonObject tiersJson, String key) {
        if (!tiersJson.has(key)) return List.of();
        if (!tiersJson.get(key).isJsonArray()) {
            throw new IllegalArgumentException("field 'tiers." + key + "' must be an array");
        }
        return readEntries(tiersJson.getAsJsonArray(key));
    }

    private static String tierKey(RewardKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
