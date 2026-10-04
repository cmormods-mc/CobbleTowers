package com.cobbletowers.ascension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Ascension's battle effects (P30) as logical {@code evs} operations for the tower-fx extension: the enemy's growth and the
 * player's matching boon. Pure; the adapters merge them with whatever armor and custom modifiers contribute.
 *
 * <p>The enemy operation is one per battle, not one per player: a boss battle merges every player's operations, so the
 * boss side would otherwise be raised once per teammate.
 */
public final class AscensionFx {

    private AscensionFx() {}

    /** The operations a player's own side gets: the boon, nothing at the base cycle. */
    public static JsonArray boon(int ascension) {
        JsonArray ops = new JsonArray();
        int amount = AscensionPolicy.boonEvs(ascension);
        if (amount > 0) ops.add(evs("self", amount));
        return ops;
    }

    /** The operation the enemy side gets, nothing at the base cycle. */
    public static JsonArray enemy(int ascension) {
        JsonArray ops = new JsonArray();
        int amount = AscensionPolicy.enemyEvs(ascension);
        if (amount > 0) ops.add(evs("foe", amount));
        return ops;
    }

    private static JsonObject evs(String side, int amount) {
        JsonObject op = new JsonObject();
        op.addProperty("op", "evs");
        op.addProperty("side", side);
        op.addProperty("amount", amount);
        return op;
    }
}
