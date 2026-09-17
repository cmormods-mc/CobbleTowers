package com.cobbletowers.api.tower.participant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The reason a participant's state is three axes rather than one enum: the facts occur together, and
 * a reconnect has to restore exactly what was left behind.
 */
class ParticipantStateTest {

    @Test
    @DisplayName("a player who just joined can fight")
    void joined() {
        ParticipantState joined = ParticipantState.joined();

        assertTrue(joined.canFight());
        assertTrue(joined.isInRun());
        assertFalse(joined.isSpectating());
    }

    @Test
    @DisplayName("a disconnect touches only the connection, so a reconnect restores what was left")
    void reconnectRestoresCombatState() {
        ParticipantState spectating = ParticipantState.joined().knockedOut().spectating();

        ParticipantState offline = spectating.disconnected();
        assertEquals(CombatState.SPECTATING_TEAM, offline.combat(), "the combat axis must survive a disconnect");
        assertFalse(offline.canFight());

        assertEquals(spectating, offline.reconnected(), "a reconnect returns exactly the state left behind");
    }

    @Test
    @DisplayName("knocked out and disconnected at once is representable")
    void bothAtOnce() {
        // The case the single enum in TDS section 8 cannot hold: it would have to forget one.
        ParticipantState state = ParticipantState.joined().knockedOut().disconnected();

        assertEquals(ConnectionState.DISCONNECTED, state.connection());
        assertEquals(CombatState.KNOCKED_OUT, state.combat());
        assertEquals(MembershipState.MEMBER, state.membership());
        assertTrue(state.isSpectating());
    }

    @Test
    @DisplayName("a rescue returns a knocked-out player to the fight at the intermission")
    void rescueFlow() {
        ParticipantState down = ParticipantState.joined().knockedOut().spectating();

        ParticipantState pending = down.revivePending();
        assertEquals(CombatState.REVIVE_PENDING, pending.combat());
        assertFalse(pending.canFight(), "they are not back until the intermission returns them");

        assertTrue(pending.revived().canFight());
    }

    @Test
    @DisplayName("leaving is irreversible: nothing moves a participant afterwards")
    void voluntaryLeaveIsTerminal() {
        ParticipantState left = ParticipantState.joined().left();

        assertFalse(left.isInRun());
        assertFalse(left.canFight());
        for (ParticipantState after : new ParticipantState[]{
                left.reconnected(), left.disconnected(), left.knockedOut(),
                left.spectating(), left.revivePending(), left.revived(), left.left()}) {
            assertSame(left, after, "a player who left the run cannot be moved back into it");
        }
    }

    @Test
    @DisplayName("only an online, active member may fight")
    void canFightIsStrict() {
        ParticipantState active = ParticipantState.joined();

        assertFalse(active.disconnected().canFight());
        assertFalse(active.knockedOut().canFight());
        assertFalse(active.left().canFight());
        assertTrue(active.canFight());
    }
}
