package com.cobbletowers.ascension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Ascension's battle effects (P30) as logical {@code evs} operations for the tower-fx extension: the enemy's growth
 * and the player's boon. Pure. The enemy operation is one per battle, since a boss battle merges every player's
 * operations.
 */
public final class AscensionFx {

    private AscensionFx() {}

    /** The operations a player's own side gets: the boon, nothing at the base cycle. */
    public static JsonArray boon(int ascension) {
        JsonArray ops = new JsonArray();
        addChunks(ops, "self", AscensionPolicy.boonEvs(ascension));
        return ops;
    }

    /** The operation the enemy side gets, nothing at the base cycle. */
    public static JsonArray enemy(int ascension) {
        JsonArray ops = new JsonArray();
        addChunks(ops, "foe", AscensionPolicy.enemyEvs(ascension));
        return ops;
    }

    /**
     * One operation carries at most {@link AscensionPolicy#MAX_EVS_PER_OPERATION}; a deeper Ascension sends several.
     */
    private static void addChunks(JsonArray ops, String side, int total) {
        for (int left = total; left > 0; left -= AscensionPolicy.MAX_EVS_PER_OPERATION) {
            ops.add(evs(side, Math.min(left, AscensionPolicy.MAX_EVS_PER_OPERATION)));
        }
    }

    private static JsonObject evs(String side, int amount) {
        JsonObject op = new JsonObject();
        op.addProperty("op", "evs");
        op.addProperty("side", side);
        op.addProperty("amount", amount);
        return op;
    }
}
