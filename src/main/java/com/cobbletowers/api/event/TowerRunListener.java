package com.cobbletowers.api.event;

import com.cobbletowers.api.tower.FloorView;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.api.tower.TowerRunView;

/**
 * What an addon is told about a run. Called on the server thread, after the change has been applied
 * and persisted, so what a listener reads is what is committed.
 *
 * <p>An exception thrown from here is contained and logged; it never stops a run advancing.
 */
public interface TowerRunListener {

    /** Every transition, including into a terminal state. */
    default void onStateChanged(TowerRunView run, RunState from, RunState to) {}

    /** A floor ended. {@code cleared} is false when the party was wiped on it. */
    default void onFloorResolved(TowerRunView run, FloorView floor, boolean cleared) {}

    /**
     * The run reached a terminal state: completed, cashed out, failed or abandoned. A run waiting in
     * RECOVERY_REQUIRED has not ended and does not fire this.
     */
    default void onRunEnded(TowerRunView run, RunState terminal) {}
}
