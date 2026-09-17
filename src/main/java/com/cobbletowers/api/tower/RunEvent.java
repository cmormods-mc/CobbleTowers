package com.cobbletowers.api.tower;

/**
 * What can happen to a tower run. Every state change is one of these applied to a {@link RunState};
 * nothing moves a run by assigning a state directly.
 */
public enum RunEvent {
    PARTY_SUBMITTED,
    PARTY_VALIDATED,
    PARTY_REJECTED,
    INSTANCE_ALLOCATED,
    ALLOCATION_FAILED,
    PREPARATION_COMPLETE,
    ENCOUNTER_STARTED,
    /** The floor's encounter ended with the party still standing. */
    ENCOUNTER_RESOLVED_CLEARED,
    /** Every participant is out: the one legitimate way a run is lost. */
    ENCOUNTER_RESOLVED_WIPED,
    REWARDS_BANKED,
    FINAL_FLOOR_CLEARED,
    INTERMISSION_COMPLETE,
    CASH_OUT_CHOSEN,
    NEXT_FLOOR_CONFIRMED,
    /** The party or an operator ended the run. Accepted from any live state. */
    ABANDON_REQUESTED,
    /** Anything the tower itself broke. Accepted from any live state; never a player loss. */
    TECHNICAL_FAILURE,
    RECOVERY_COMPLETED,
    RECOVERY_ABANDONED
}
