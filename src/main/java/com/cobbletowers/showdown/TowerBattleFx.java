package com.cobbletowers.showdown;

import com.cobbletowers.TowerLog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The battle effects waiting for a player's tower battle, and how they reach Showdown (P23).
 *
 * <p>The effects are <b>queued</b> by whoever decides them (an operator seam today, a worn armor set in P24) and
 * only <b>armed</b> by the code that is about to start a tower battle, immediately before it does and disarmed
 * immediately after. The format-field provider hands out nothing but armed effects, once. That two-step shape is
 * the guarantee: an effect can never leak into a battle that was not a tower battle (a wild battle the player
 * wanders into next), into a later battle, or into someone else's.
 *
 * <p>Operations use <b>logical</b> sides here -- {@code self}, {@code foe}, {@code both} -- and are resolved to
 * Showdown's concrete side ids ({@code p1}, {@code p2}...) when armed, because only the code starting the battle
 * knows who is who (a floor battle is p1 against p2; a boss raid is p1..pN against p(N+1)). The JavaScript never
 * has to guess. Every operation is validated here as well as in {@code tower-fx.js}: a bad one is dropped and
 * logged, never forwarded.
 *
 * <p>Pure apart from the maps: no Minecraft or Cobblemon types, so every rule is a unit test.
 */
public final class TowerBattleFx {

    /** The same cap tower-fx.js enforces; anything past it would only be ignored there. */
    public static final int MAX_OPS = 32;

    private static final Set<String> WEATHERS = Set.of("raindance", "sunnyday", "sandstorm", "hail", "snowscape");
    private static final Set<String> TERRAINS = Set.of("electricterrain", "grassyterrain", "mistyterrain", "psychicterrain");
    private static final Set<String> STATS = Set.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion");
    private static final Set<String> STATUSES = Set.of("brn", "par", "psn", "tox", "slp", "frz");
    private static final Set<String> SIDE_CONDITIONS = Set.of("tailwind", "reflect", "lightscreen", "auroraveil", "safeguard", "mist");
    /** The eighteen types as Showdown names them. */
    private static final Set<String> TYPES = Set.of("Normal", "Fire", "Water", "Electric", "Grass", "Ice", "Fighting", "Poison",
            "Ground", "Flying", "Psychic", "Bug", "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy");

    /** Waiting for a player's next tower battle: logical operations. */
    private static final Map<UUID, JsonArray> QUEUED = new ConcurrentHashMap<>();
    /** Armed for the battle being started right now: resolved operations, by player. */
    private static final Map<UUID, JsonArray> ARMED = new ConcurrentHashMap<>();

    private TowerBattleFx() {}

    // ---- queueing ------------------------------------------------------------------------------------------------

    /**
     * Replaces whatever is queued for {@code player} with these logical operations.
     *
     * @return how many survived validation
     */
    public static int queue(UUID player, JsonArray logical) {
        JsonArray valid = new JsonArray();
        for (JsonElement element : logical) {
            if (valid.size() >= MAX_OPS) break;
            if (validate(element).isPresent()) valid.add(element);
        }
        if (valid.isEmpty()) {
            QUEUED.remove(player);
        } else {
            QUEUED.put(player, valid);
        }
        return valid.size();
    }

    public static Optional<JsonArray> queued(UUID player) {
        return Optional.ofNullable(QUEUED.get(player)).map(JsonArray::deepCopy);
    }

    public static void clearQueued(UUID player) {
        QUEUED.remove(player);
    }

    // ---- arming ---------------------------------------------------------------------------------------------------

    /** A floor's own battle is p1 (the player) against p2 (the opponent). */
    public static void armFloorBattle(UUID player) {
        armFloorBattle(player, new JsonArray());
    }

    /**
     * The same, with {@code extra} logical operations the caller derived from the live player (a worn armor set,
     * P24) merged after whatever is queued. The queue is the operator seam; the extras are never stored.
     */
    public static void armFloorBattle(UUID player, JsonArray extra) {
        arm(player, List.of("p1"), List.of("p2"), extra);
    }

    /**
     * A boss raid is p1..pN (the players, in request order) against p(N+1). Each player's own effects are resolved
     * against <b>their</b> side, so one player's bonus never lands on a teammate.
     */
    public static void armBossBattle(List<UUID> players) {
        armBossBattle(players, player -> new JsonArray());
    }

    /** As above, with each player's own derived operations ({@code extras}). */
    public static void armBossBattle(List<UUID> players, java.util.function.Function<UUID, JsonArray> extras) {
        String boss = "p" + (players.size() + 1);
        for (int i = 0; i < players.size(); i++) {
            arm(players.get(i), List.of("p" + (i + 1)), List.of(boss), extras.apply(players.get(i)));
        }
    }

    private static void arm(UUID player, List<String> self, List<String> foe, JsonArray extra) {
        JsonArray logical = new JsonArray();
        JsonArray queued = QUEUED.get(player);
        if (queued != null) queued.forEach(logical::add);
        extra.forEach(logical::add);
        if (logical.isEmpty()) return;
        JsonArray resolved = resolve(logical, self, foe);
        if (!resolved.isEmpty()) ARMED.put(player, resolved);
    }

    /** Called straight after the battle start, whether or not it worked: nothing armed may outlive the call. */
    public static void disarm(UUID player) {
        ARMED.remove(player);
    }

    public static void disarmAll(List<UUID> players) {
        for (UUID player : players) ARMED.remove(player);
    }

    // ---- handing to Showdown ----------------------------------------------------------------------------------------

    /**
     * The CobbleRaids format-field provider: the {@code towerFx} field of a battle whose players have armed effects.
     * Consumes them. Returns nothing for a battle that is not a tower battle, which is every other battle.
     */
    public static Map<String, String> fieldsFor(UUID battleId, List<UUID> players) {
        JsonArray all = new JsonArray();
        for (UUID player : players) {
            JsonArray armed = ARMED.remove(player);
            if (armed == null) continue;
            for (JsonElement op : armed) {
                if (all.size() < MAX_OPS) all.add(op);
            }
        }
        if (all.isEmpty()) return Map.of();
        TowerLog.info("Tower battle {} carries {} effect(s): {}", battleId, all.size(), all);
        return Map.of("towerFx", all.toString());
    }

    // ---- validation and resolution ------------------------------------------------------------------------------------

    /**
     * Returns the operation as a clean object (only the parameters its kind takes, each in range), or empty if it
     * is not an operation we will send. Unknown operations are refused here too: the JavaScript ignores them, but
     * there is no reason to send them.
     */
    public static Optional<JsonObject> validate(JsonElement element) {
        if (element == null || !element.isJsonObject()) return Optional.empty();
        JsonObject op = element.getAsJsonObject();
        String name = string(op, "op");
        if (name == null) return Optional.empty();
        JsonObject clean = new JsonObject();
        clean.addProperty("op", name);
        switch (name) {
            case "weather" -> {
                if (!oneOf(op, "id", WEATHERS, clean)) return Optional.empty();
                copyInt(op, "duration", 0, 20, clean);
            }
            case "terrain" -> {
                if (!oneOf(op, "id", TERRAINS, clean)) return Optional.empty();
                copyInt(op, "duration", 0, 20, clean);
            }
            case "boost" -> {
                if (!side(op, clean) || !oneOf(op, "stat", STATS, clean) || !copyInt(op, "stages", -6, 6, clean)) return Optional.empty();
            }
            case "hp" -> {
                if (!side(op, clean) || !copyInt(op, "percent", 1, 100, clean)) return Optional.empty();
            }
            case "status" -> {
                if (!side(op, clean) || !oneOf(op, "status", STATUSES, clean)) return Optional.empty();
            }
            case "sidecondition" -> {
                if (!side(op, clean) || !oneOf(op, "id", SIDE_CONDITIONS, clean)) return Optional.empty();
                copyInt(op, "duration", 0, 20, clean);
            }
            case "damage", "resist" -> {
                if (!side(op, clean) || !copyInt(op, "percent", 1, 300, clean)) return Optional.empty();
                String type = string(op, "type");
                if (type != null && !type.equals("any") && !TYPES.contains(type)) return Optional.empty();
                clean.addProperty("type", type == null ? "any" : type);
            }
            default -> {
                return Optional.empty();
            }
        }
        return Optional.of(clean);
    }

    /** Logical operations to concrete ones: {@code side} becomes {@code sides}, a list of Showdown side ids. */
    static JsonArray resolve(JsonArray logical, List<String> self, List<String> foe) {
        JsonArray out = new JsonArray();
        for (JsonElement element : logical) {
            if (out.size() >= MAX_OPS) break;
            Optional<JsonObject> valid = validate(element);
            if (valid.isEmpty()) continue;
            JsonObject op = valid.get();
            if (op.has("side")) {
                List<String> ids = new ArrayList<>();
                switch (op.get("side").getAsString()) {
                    case "self" -> ids.addAll(self);
                    case "foe" -> ids.addAll(foe);
                    default -> {
                        ids.addAll(self);
                        ids.addAll(foe);
                    }
                }
                op.remove("side");
                JsonArray sides = new JsonArray();
                ids.forEach(sides::add);
                op.add("sides", sides);
            }
            out.add(op);
        }
        return out;
    }

    // ---- small readers ------------------------------------------------------------------------------------------------

    private static String string(JsonObject op, String key) {
        JsonElement value = op.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static boolean oneOf(JsonObject op, String key, Set<String> allowed, JsonObject clean) {
        String value = string(op, key);
        if (value == null || !allowed.contains(value)) return false;
        clean.addProperty(key, value);
        return true;
    }

    /** Copies an integer in range; an absent value is fine for an optional one, an out-of-range value is clamped. */
    private static boolean copyInt(JsonObject op, String key, int low, int high, JsonObject clean) {
        JsonElement value = op.get(key);
        if (value == null) return false;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        double raw = value.getAsDouble();
        if (Double.isNaN(raw) || Double.isInfinite(raw)) return false;
        clean.addProperty(key, (int) Math.max(low, Math.min(high, Math.round(raw))));
        return true;
    }

    private static boolean side(JsonObject op, JsonObject clean) {
        String side = string(op, "side");
        if (side == null || !(side.equals("self") || side.equals("foe") || side.equals("both"))) return false;
        clean.addProperty("side", side);
        return true;
    }
}
