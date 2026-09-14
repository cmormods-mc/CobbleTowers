package com.cobbletowers.run;

import java.util.Objects;

/** Immutable persisted location used to return a player after leaving or being eliminated. */
public record TowerReturnLocation(
        String dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public TowerReturnLocation {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (dimensionId.isBlank()) throw new IllegalArgumentException("dimensionId may not be blank");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("return coordinates must be finite");
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("return rotation must be finite");
        }
    }
}
