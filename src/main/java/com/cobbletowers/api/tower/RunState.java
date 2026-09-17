package com.cobbletowers.api.tower;

/**
 * Where a tower run is. The nine core states of the run's life, plus the branches it can end on.
 *
 * <p>A run's state is owned by the server and moved only by {@code RunTransitions}; see
 * {@code docs/design/P1-contracts.md} for the table.
 */
public enum RunState {
    /** The run object exists; nothing has been validated or allocated. */
    CREATED,
    VALIDATING_PARTY,
    ALLOCATING_INSTANCE,
    /** Registration is locked and the party is preparing; untimed in normal mode. */
    PREPARING,
    FLOOR_READY,
    ENCOUNTER_ACTIVE,
    FLOOR_RESOLVING,
    INTERMISSION,
    NEXT_FLOOR_READY,

    /** Every floor cleared. */
    COMPLETED,
    /** The party banked its rewards and stopped deliberately. */
    CASHED_OUT,
    /** Legitimate gameplay defeat: nobody could field a Pokemon. */
    FAILED,
    /** Ended by the party or an operator, rather than by the tower. */
    ABANDONED,
    /**
     * A technical failure -- battle initialization, a missing entity, a crash, an adapter fault.
     * Never reported as a player loss, and always resumable from the last checkpoint.
     */
    RECOVERY_REQUIRED;

    /** True when no event leads anywhere from here. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CASHED_OUT || this == FAILED || this == ABANDONED;
    }

    /** True while the run is neither finished nor waiting for recovery. */
    public boolean isLive() {
        return !isTerminal() && this != RECOVERY_REQUIRED;
    }
}
