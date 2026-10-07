package com.cobbletowers.api.modifier;

import java.util.List;
import java.util.Optional;

/** Everything a run has drafted, and whatever it is being offered right now. */
public interface RunModifiersView {

    /**
     * What the run is carrying, in draft order; a modifier held twice appears twice, up to its {@link
     * ModifierView#stackLimit()}.
     */
    List<ModifierView> accumulated();

    /** The open draft, if the run is sitting at one. */
    Optional<DraftView> openDraft();

    /** How many challenges the run has accumulated: the number drafted, not distinct modifiers (TDS #57). */
    int challengeCount();
}
