package com.cobbletowers.api.tower;

import com.cobbletowers.api.tower.participant.ParticipantState;
import java.util.List;
import java.util.UUID;

/** One player in a run, as an addon may read them. */
public interface ParticipantView {

    UUID playerId();

    /** Connection, combat and membership; see {@link ParticipantState}. */
    ParticipantState state();

    /** The Pokemon this player registered, by Cobblemon uuid, in registration order; locked at party validation. */
    List<UUID> registeredPokemon();
}
