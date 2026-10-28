package com.cobbletowers.halloween.vault;

import com.google.gson.JsonObject;

/** One entry of events.json: fires on tick {@code tick} of state {@code state}, for outcome {@code outcome} ("any", "treat", "trick"). */
public record VaultEvent(VaultMachine.State state, int tick, String outcome, String type, JsonObject data) {

    public static VaultEvent parse(JsonObject o) {
        return new VaultEvent(VaultMachine.State.valueOf(o.get("state").getAsString()), o.get("tick").getAsInt(), o.get("outcome").getAsString(),
                o.get("type").getAsString(), o);
    }

    public boolean appliesTo(VaultMachine.Outcome outcome) {
        return this.outcome.equals("any") || (outcome != null && this.outcome.equals(outcome.name().toLowerCase(java.util.Locale.ROOT)));
    }
}
