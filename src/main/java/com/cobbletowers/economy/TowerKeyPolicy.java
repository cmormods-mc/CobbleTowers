package com.cobbletowers.economy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Whether starting a run costs the tower key, and for which runs. Pure, so the rules read without a server.
 *
 * <p>Only an <b>ordinary</b> run costs a key: a new run, a run code or an Ascension start. A trial has its own
 * gating (an attempt a day), a rental run is a draft the player has already invested in, and resuming a parked run
 * is not a start at all.
 */
public final class TowerKeyPolicy {

    private TowerKeyPolicy() {}

    /** Whether a run started under these conditions costs each player one key, given the operator's setting. */
    public static boolean costsKey(boolean required, boolean trial, boolean rental) {
        return required && !trial && !rental;
    }

    /**
     * Reads {@code {"required": true}} from {@code config/cobbletowers-keys.json}. Absent or empty text means not
     * required; unreadable text throws, and the caller treats that as not required too and says so: a typo must never
     * lock every player out of the towers.
     */
    public static boolean parseRequired(String json) {
        if (json == null || json.isBlank()) return false;
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return root.has("required") && root.get("required").getAsBoolean();
    }
}
