package com.cobbletowers.api.tower;

import com.cobbletowers.api.tower.participant.ParticipantState;
import java.util.List;
import java.util.UUID;

/** One player in a run, as an addon may read them. */
public interface ParticipantView {

    UUID playerId();

    /** Connection, combat and membership; see {@link ParticipantState}. */
    ParticipantState state();

    /**
     * The Pokemon this player registered, by Cobblemon's own uuids, in registration order. Locked
     * when the run's party was validated, so it does not follow later party edits.
     */
    List<UUID> registeredPokemon();
}
