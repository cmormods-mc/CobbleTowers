package com.cobbletowers.run;

import java.util.Objects;
import java.util.UUID;

/** Persistent run membership and return metadata for one participant. */
public record TowerParticipant(UUID playerId, TowerReturnLocation returnLocation, boolean active) {
    public TowerParticipant {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(returnLocation, "returnLocation");
    }

    public TowerParticipant deactivate() {
        return active ? new TowerParticipant(playerId, returnLocation, false) : this;
    }
}
