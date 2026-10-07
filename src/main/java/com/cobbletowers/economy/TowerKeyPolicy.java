package com.cobbletowers.economy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Whether starting a run costs the tower key. Only an ordinary run does (new run, run code, Ascension start); trials
 * have their own gating, rental runs are already an investment and resuming is not a start. Pure.
 */
public final class TowerKeyPolicy {

    private TowerKeyPolicy() {}

    /** Whether a run started under these conditions costs each player one key, given the operator's setting. */
    public static boolean costsKey(boolean required, boolean trial, boolean rental) {
        return required && !trial && !rental;
    }

    /**
     * Reads {@code {"required": true}} from {@code config/cobbletowers-keys.json}. Absent or empty means not
     * required; unreadable text throws and the caller treats it as not required, so a typo cannot lock players out.
     */
    public static boolean parseRequired(String json) {
        if (json == null || json.isBlank()) return false;
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return root.has("required") && root.get("required").getAsBoolean();
    }
}
