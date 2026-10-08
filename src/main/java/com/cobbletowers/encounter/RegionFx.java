package com.cobbletowers.encounter;

import com.cobbletowers.definition.FloorDefinition;
import com.cobbletowers.definition.RulesetDefinition;
import com.cobbletowers.definition.RulesetResolver;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.persistence.PersistedRun;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * A tower's region rules (P38) as battle operations: Tideforge's no held items, Rootvale's enemy drain, Duskvale's
 * floor status. {@link #build} is pure; {@link #ops} reads the run's ruleset. The floor's status is put on the lead at the
 * start of EVERY fight of the floor (opponents and boss alike); a Pokemon that already has a status keeps it, so a cure
 * used between fights is answered by the next fight.
 */
public final class RegionFx {

    private RegionFx() {}

    /**
     * The operations for one battle of {@code run}, for {@code player}'s side. {@code enemySide} is true for exactly one
     * caller per battle (a boss battle merges every player's operations), so the drain is added once.
     */
    public static JsonArray ops(PersistedRun run, UUID player, boolean enemySide) {
        TowerContent content = TowerDefinitionRegistry.content();
        TowerDefinition tower = content.towers().get(run.towerId());
        if (tower == null) return new JsonArray();
        Optional<FloorDefinition> floor = content.floorAt(run.towerId(), run.floorIndex());
        RulesetDefinition rules = RulesetResolver.forRun(content, run, floor.flatMap(FloorDefinition::rulesetOverride));
        if (rules == null) return new JsonArray();
        return build(rules, tower.contentFloor(run.floorIndex()), tower.floorCount(), run.seed(), run.floorIndex(), enemySide);
    }

    /** Pure: the operations a ruleset asks for on this floor. */
    public static JsonArray build(RulesetDefinition rules, int contentFloor, int floorCount, long seed, int runFloor,
                                  boolean enemySide) {
        JsonArray out = new JsonArray();
        if (!rules.playerHeldItems()) out.add(op("suppress_items", "self"));
        int drain = rules.enemyDrainPercent(contentFloor, floorCount);
        if (enemySide && drain > 0) {
            JsonObject op = op("drain", "foe");
            op.addProperty("percent", drain);
            out.add(op);
        }
        if (!rules.floorStatuses().isEmpty()) {
            JsonObject op = op("status", "self");
            op.addProperty("status", statusFor(rules.floorStatuses(), seed, runFloor));
            out.add(op);
        }
        return out;
    }

    /** The floor's status: from the run seed and the floor, so a replay of the run gives the same one. */
    public static String statusFor(List<String> statuses, long seed, int runFloor) {
        return statuses.get(new Random(seed * 31L + runFloor * 0x9E3779B97F4A7C15L).nextInt(statuses.size()));
    }

    private static JsonObject op(String name, String side) {
        JsonObject op = new JsonObject();
        op.addProperty("op", name);
        op.addProperty("side", side);
        return op;
    }
}
