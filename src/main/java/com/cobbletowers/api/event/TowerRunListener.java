package com.cobbletowers.api.event;

import com.cobbletowers.api.tower.FloorView;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.api.tower.TowerRunView;

/**
 * What an addon is told about a run. Called on the server thread after the change is applied and persisted. An
 * exception is contained and logged.
 */
public interface TowerRunListener {

    /** Every transition, including into a terminal state. */
    default void onStateChanged(TowerRunView run, RunState from, RunState to) {}

    /** A floor ended. {@code cleared} is false when the party was wiped on it. */
    default void onFloorResolved(TowerRunView run, FloorView floor, boolean cleared) {}

    /**
     * The run reached a terminal state: completed, cashed out, failed or abandoned. Not fired for RECOVERY_REQUIRED.
     */
    default void onRunEnded(TowerRunView run, RunState terminal) {}
}
