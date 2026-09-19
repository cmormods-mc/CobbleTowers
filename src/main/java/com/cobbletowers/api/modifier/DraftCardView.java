package com.cobbletowers.api.modifier;

import java.util.List;
import java.util.UUID;

/** One of the three cards on offer, and who has voted for it. */
public interface DraftCardView {

    /** Which card this is, 0-based, in the order the draft offers them. */
    int index();

    ModifierView modifier();

    /** The players who have voted for this card. Never contains a player twice. */
    List<UUID> votes();
}
