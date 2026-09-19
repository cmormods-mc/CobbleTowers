package com.cobbletowers.api.modifier;

/**
 * What part of a run a modifier changes (TDS #56).
 *
 * <p>The taxonomy exists so the engine never grows a monolithic conditional: a modifier is applied
 * by whatever owns its type, and nothing has to ask "which modifier is this?".
 *
 * <p><b>All five are declared from the start, though two do nothing yet.</b> {@link
 * #PLAYER_CONSTRAINT} and {@link #FIELD} change the rules of a battle, which CobbleTowers
 * deliberately cannot do -- reaching into Showdown belongs to the one mod already there (TDS #48).
 * They take effect in P8b, through the CobbleRaids encounter boundary. Declaring them inert now is
 * honest about the plan; inventing them later would be a schema change breaking content written
 * against this one.
 */
public enum ModifierType {

    /** The opponents themselves: level, count, how a pool is weighted. */
    ENEMY,

    /** The shape of a floor's encounters rather than the opponents in them. */
    ENCOUNTER,

    /** What the party may do in a battle: items, switching, banned moves. Effective in P8b. */
    PLAYER_CONSTRAINT,

    /** Weather, terrain and other battlefield conditions. Effective in P8b. */
    FIELD,

    /**
     * What the run earns.
     *
     * <p>Recorded, never valued. The unclaimed pool has recorded what happened and not what it is
     * worth since P5, because the economy is P9's; a reward modifier is recorded the same way.
     */
    REWARD
}
