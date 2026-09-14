package com.cobbletowers.run;

import java.util.Objects;
import java.util.UUID;

/** Persistent run membership, reconnect state, and return metadata for one participant. */
public record TowerParticipant(
        UUID playerId,
        TowerReturnLocation returnLocation,
        boolean active,
        boolean connected,
        int reconnectGraceTicksRemaining
) {
    public static final int DEFAULT_RECONNECT_GRACE_TICKS = 5 * 60 * 20;

    public TowerParticipant {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(returnLocation, "returnLocation");
        if (reconnectGraceTicksRemaining < 0) {
            throw new IllegalArgumentException("reconnectGraceTicksRemaining must be >= 0");
        }
        if (!active && connected) {
            throw new IllegalArgumentException("Eliminated participants cannot remain connected to a Tower run");
        }
    }

    /** Compatibility constructor for a newly created active participant. */
    public TowerParticipant(UUID playerId, TowerReturnLocation returnLocation, boolean active) {
        this(playerId, returnLocation, active, active, DEFAULT_RECONNECT_GRACE_TICKS);
    }

    public TowerParticipant disconnect() {
        if (!active || !connected) return this;
        return new TowerParticipant(playerId, returnLocation, true, false, DEFAULT_RECONNECT_GRACE_TICKS);
    }

    public TowerParticipant reconnect() {
        if (!active || connected) return this;
        return new TowerParticipant(playerId, returnLocation, true, true, DEFAULT_RECONNECT_GRACE_TICKS);
    }

    public TowerParticipant decrementReconnectGrace() {
        if (!active || connected || reconnectGraceTicksRemaining == 0) return this;
        return new TowerParticipant(playerId, returnLocation, true, false, reconnectGraceTicksRemaining - 1);
    }

    public boolean reconnectGraceExpired() {
        return active && !connected && reconnectGraceTicksRemaining == 0;
    }

    public TowerParticipant deactivate() {
        return active ? new TowerParticipant(playerId, returnLocation, false, false, 0) : this;
    }
}
