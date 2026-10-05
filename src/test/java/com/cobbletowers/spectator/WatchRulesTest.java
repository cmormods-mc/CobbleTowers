package com.cobbletowers.spectator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.spectator.WatchRules.Situation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WatchRulesTest {

    private static final Situation OK = new Situation(true, false, true, true, false, false);

    @Test
    @DisplayName("an ordinary request to watch a live run is allowed")
    void allowed() {
        assertTrue(WatchRules.refusal(OK).isEmpty());
    }

    @Test
    @DisplayName("each reason to refuse is said in its own words")
    void refusals() {
        assertEquals("You cannot watch yourself.", WatchRules.refusal(new Situation(true, true, true, true, false, false)).orElseThrow());
        assertEquals("You are in a run of your own. Finish it or cash out first.",
                WatchRules.refusal(new Situation(true, false, true, true, true, false)).orElseThrow());
        assertEquals("That player is not online.", WatchRules.refusal(new Situation(false, false, true, true, false, false)).orElseThrow());
        assertEquals("That player is not in a tower run right now.",
                WatchRules.refusal(new Situation(true, false, false, false, false, false)).orElseThrow());
        assertEquals("That player is between floors; try again in a moment.",
                WatchRules.refusal(new Situation(true, false, true, false, false, false)).orElseThrow());
    }

    @Test
    @DisplayName("someone already watching may switch to another player")
    void switching() {
        assertTrue(WatchRules.refusal(new Situation(true, false, true, true, false, true)).isEmpty());
    }

    @Test
    @DisplayName("a watch ends when the run is over, or the followed player has gone or left the tower")
    void endings() {
        assertFalse(WatchRules.mustEnd(true, true, true));
        assertTrue(WatchRules.mustEnd(false, true, true));
        assertTrue(WatchRules.mustEnd(true, false, false));
        assertTrue(WatchRules.mustEnd(true, true, false));
    }
}
