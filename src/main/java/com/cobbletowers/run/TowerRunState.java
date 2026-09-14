package com.cobbletowers.run;

/**
 * Server-authoritative lifecycle for one Tower run.
 *
 * <p>Transitions are intentionally explicit so event handlers cannot accidentally advance a run
 * twice. PREPARING_NEXT_FLOOR and BOSS_BATTLE are durable recovery markers: after an unclean server
 * stop, world reconciliation can rebuild the pending floor or restart the interrupted boss without
 * attempting to serialize live Cobblemon/Showdown battle internals.
 */
public enum TowerRunState {
    PREPARING,
    FLOOR_ACTIVE,
    BOSS_BATTLE,
    CHOOSING_UPGRADE,
    READY_FOR_NEXT_FLOOR,
    PREPARING_NEXT_FLOOR,
    COMPLETE,
    FAILED,
    ABORTED;

    public boolean terminal() {
        return this == COMPLETE || this == FAILED || this == ABORTED;
    }
}
