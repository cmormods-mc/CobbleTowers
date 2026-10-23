package com.cobbletowers.economy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Which key starting a run costs: the tower key for an ordinary run (new run, run code, Ascension start), the rental
 * key for a rental-draft run. Trials have their own gating and resuming is not a start. Pure.
 */
public final class TowerKeyPolicy {

    private TowerKeyPolicy() {}

    /** Which key a run started under these conditions costs each player, given the operator's setting. */
    public static java.util.Optional<TowerKeys.Kind> keyFor(boolean required, boolean trial, boolean rental) {
        if (!required || trial) return java.util.Optional.empty();
        return java.util.Optional.of(rental ? TowerKeys.Kind.RENTAL : TowerKeys.Kind.TOWER);
    }

    /**
     * Whether a player's free daily run is still unused. {@code lastFreeDay} is the day key of their last free run
     * (empty if never) and {@code today} the current trial day key, both from {@code TrialClock.dayKey}. A stored day
     * after today (clock moved back) still frees a run: forgiving over exact.
     */
    public static boolean freeRunAvailable(java.util.Optional<String> lastFreeDay, String today) {
        // Any other day than today counts, so a moved reset hour or a clock step back never locks a player out for days.
        return lastFreeDay.map(day -> !day.equals(today)).orElse(true);
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
