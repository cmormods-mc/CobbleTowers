package com.cobbletowers.api.tower.participant;

/**
 * What a participant may do in the run. Never a Minecraft death: a knocked-out player is a tower
 * spectator, not a corpse (TDS §8).
 */
public enum CombatState {
    /** Fighting, or able to.  */
    ACTIVE,
    /** Their whole registered party fainted. */
    KNOCKED_OUT,
    /** Watching teammates, able to cycle between them (TDS #25). */
    SPECTATING_TEAM,
    /** Teammates cleared the floor; they return at the next intermission (TDS §8). */
    REVIVE_PENDING
}
