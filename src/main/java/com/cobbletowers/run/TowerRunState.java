package com.cobbletowers.run;

/**
 * Server-authoritative lifecycle for one Tower run.
 *
 * <p>Transitions are intentionally explicit so tick/event handlers cannot accidentally advance a run
 * twice. World placement and battle code consume this state; they do not own it.
 */
public enum TowerRunState {
    PREPARING,
    FLOOR_ACTIVE,
    CHOOSING_UPGRADE,
    READY_FOR_NEXT_FLOOR,
    COMPLETE,
    FAILED,
    ABORTED;

    public boolean terminal() {
        return this == COMPLETE || this == FAILED || this == ABORTED;
    }
}
