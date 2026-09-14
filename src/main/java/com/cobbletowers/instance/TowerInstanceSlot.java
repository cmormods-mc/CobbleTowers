package com.cobbletowers.instance;

import java.util.Objects;
import java.util.UUID;

/** Immutable allocation result for one private Tower run region. */
public record TowerInstanceSlot(UUID runId, int slotIndex, int originX, int originZ) {
    public TowerInstanceSlot {
        Objects.requireNonNull(runId, "runId");
        if (slotIndex < 0) throw new IllegalArgumentException("slotIndex must be >= 0");
    }
}
