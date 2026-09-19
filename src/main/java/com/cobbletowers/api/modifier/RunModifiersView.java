package com.cobbletowers.api.modifier;

import java.util.List;
import java.util.Optional;

/** Everything a run has drafted, and whatever it is being offered right now. */
public interface RunModifiersView {

    /**
     * What the run is carrying, in the order it was drafted.
     *
     * <p>A modifier held more than once appears once per copy, up to its {@link
     * ModifierView#stackLimit()}.
     */
    List<ModifierView> accumulated();

    /** The open draft, if the run is sitting at one. */
    Optional<DraftView> openDraft();

    /**
     * How many challenges the run has accumulated.
     *
     * <p>The count TDS #57 counts to five on. It is the number drafted, not the number of distinct
     * modifiers held, so taking a second copy of something still moves the run toward its lock-in.
     */
    int challengeCount();
}
