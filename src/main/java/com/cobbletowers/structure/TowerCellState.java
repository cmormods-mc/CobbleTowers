package com.cobbletowers.structure;

/**
 * Durable lifecycle of one allocated private Tower cell.
 *
 * <p>The state is intentionally independent from Minecraft world objects. It exists so placement,
 * admission, cleanup, restart recovery, and slot release can agree on one authoritative lifecycle.
 * Slot release is deliberately not represented by an enum value: release occurs only after teardown
 * has completed successfully and the instance allocator is explicitly released.
 */
public enum TowerCellState {
    ALLOCATED,
    BUILDING,
    READY,
    ACTIVE,
    TEARDOWN,
    QUARANTINED;

    public boolean admitsPlayers() {
        return this == READY || this == ACTIVE;
    }
}
