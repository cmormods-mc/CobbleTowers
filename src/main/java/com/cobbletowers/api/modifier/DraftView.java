package com.cobbletowers.api.modifier;

import java.util.List;
import java.util.Optional;

/** A draft in progress: the cards on offer and how the party voted (TDS #2). */
public interface DraftView {

    /** The floor whose intermission opened this draft. */
    int floorIndex();

    /** Whether this is the every-fifth Lock-In Draft (TDS #57) rather than an ordinary one. */
    boolean lockIn();

    /** The cards on offer, in a stable order. */
    List<DraftCardView> cards();

    /** The card that won once resolved; empty while open. A resolved draft keeps its cards. */
    Optional<DraftCardView> chosen();

    /** Whether the winner was decided by the seed rather than by a majority. */
    boolean decidedByTieBreak();
}
