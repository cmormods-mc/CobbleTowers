package com.cobbletowers.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.runtime.ExitRules.Standing;
import com.cobbletowers.runtime.ExitRules.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Nobody stays in the tower dimension without a live place in a run. */
class ExitRulesTest {

    private static final long END = 100_000L;

    @Test
    @DisplayName("a player outside the tower is never touched, whatever their standing")
    void outsideIsLeftAlone() {
        assertEquals(Verdict.STAY, ExitRules.decide(false, false, Standing.none(), END));
        assertEquals(Verdict.STAY, ExitRules.decide(false, false, Standing.ended(0), END));
    }

    @Test
    @DisplayName("an active member of a live run stays")
    void activeStays() {
        assertEquals(Verdict.STAY, ExitRules.decide(true, false, Standing.active(), END));
    }

    @Test
    @DisplayName("after a run ends the players wait out the beat and then leave")
    void beatThenLeave() {
        assertEquals(Verdict.WAIT, ExitRules.decide(true, false, Standing.ended(END), END));
        assertEquals(Verdict.WAIT, ExitRules.decide(true, false, Standing.ended(END), END + ExitRules.BEAT_MILLIS - 1));
        assertEquals(Verdict.LEAVE, ExitRules.decide(true, false, Standing.ended(END), END + ExitRules.BEAT_MILLIS));
    }

    @Test
    @DisplayName("someone who left on purpose, or has no run at all, goes at once")
    void noRunLeavesImmediately() {
        assertEquals(Verdict.LEAVE, ExitRules.decide(true, false, Standing.none(), END));
    }

    @Test
    @DisplayName("an operator in creative or spectator mode is left alone even with no run")
    void operatorsAreExempt() {
        assertEquals(Verdict.STAY, ExitRules.decide(true, true, Standing.none(), END));
        assertEquals(Verdict.STAY, ExitRules.decide(true, true, Standing.ended(0), END));
    }

    @Test
    @DisplayName("an operator exploring the tower is exempt, but one who was inside when their own run ended is not")
    void operatorExemptionEndsWithTheirOwnRun() {
        assertTrue(ExitRules.exempt(true, false), "an operator in creative or spectator who walked in is exploring");
        assertFalse(ExitRules.exempt(true, true), "the end of their own run is not a visit: they leave like anyone else");
        assertFalse(ExitRules.exempt(false, false), "an ordinary player is never exempt");
        assertFalse(ExitRules.exempt(false, true));
    }

    @Test
    @DisplayName("once no longer exempt, an operator waits out the beat and then leaves, exactly as a player does")
    void leavingOperatorFollowsTheBeat() {
        boolean exempt = ExitRules.exempt(true, true);
        assertEquals(Verdict.WAIT, ExitRules.decide(true, exempt, Standing.ended(END), END));
        assertEquals(Verdict.LEAVE, ExitRules.decide(true, exempt, Standing.ended(END), END + ExitRules.BEAT_MILLIS));
        assertEquals(Verdict.STAY, ExitRules.decide(true, ExitRules.exempt(true, false), Standing.ended(END), END + 10 * 60_000L),
                "while an operator exploring long after any run stays");
    }

    @Test
    @DisplayName("a restart long after the end finds the beat already over, so the player leaves straight away")
    void longAfterTheEnd() {
        assertEquals(Verdict.LEAVE, ExitRules.decide(true, false, Standing.ended(0), 10 * 60_000L));
    }

    @Test
    @DisplayName("a cell is released at once when empty, and otherwise only after the beat")
    void releaseTiming() {
        assertTrue(ExitRules.releaseDue(false, END, END), "nobody inside: nothing to wait for");
        assertFalse(ExitRules.releaseDue(true, END, END + 1000), "someone is inside and the beat is not over");
        assertTrue(ExitRules.releaseDue(true, END, END + ExitRules.BEAT_MILLIS), "the beat is over");
    }
}
