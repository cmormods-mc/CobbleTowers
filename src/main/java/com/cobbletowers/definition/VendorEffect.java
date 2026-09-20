package com.cobbletowers.definition;

/**
 * What a vendor purchase does to a target's live party (TDS #20 C: direct recovery, never a handed-
 * over item). Closed on purpose -- adding a third kind of service is a decision for its own review,
 * not a string another definition can name into existence.
 */
public enum VendorEffect {
    /** Every Pokemon in the target's party is set to full current health. */
    FULL_HEAL,
    /** Every persistent status condition in the target's party is cleared. */
    CURE_STATUS
}
