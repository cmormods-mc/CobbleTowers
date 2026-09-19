package com.cobbletowers.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.api.tower.participant.CombatState;
import com.cobbletowers.api.tower.participant.ConnectionState;
import com.cobbletowers.api.tower.participant.MembershipState;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The participant rules, which are all of the behaviour and none of the server. */
class ParticipantServiceTest {

    private static final UUID RUN = UUID.fromString("77777777-0000-0000-0000-000000000007");
    private static final UUID ONE = TestRuns.PLAYER;
    private static final UUID TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** A run with two players, both freshly joined. */
    private static PersistedRun twoPlayers() {
        PersistedRun run = TestRuns.fresh(RUN);
        List<PersistedParticipant> both = new ArrayList<>(run.participants());
        both.add(new PersistedParticipant(TWO, ParticipantState.joined(), List.of()));
        return new PersistedRun(run.runId(), run.schemaVersion(), run.towerId(), run.towerRevision(),
                run.towerDigest(), run.rulesetRevision(), run.structureRevision(), run.seed(), run.floorIndex(),
                run.state(), both, run.lastCheckpoint(), run.committedTransactions(), run.updatedAt(), run.cell(),
                run.ledger());
    }

    private static ParticipantState stateOf(PersistedRun run, UUID playerId) {
        return ParticipantService.stateOf(run, playerId).orElseThrow();
    }

    private static PersistedRun change(PersistedRun run, UUID playerId, UnaryOperator<ParticipantState> change) {
        return ParticipantService.changed(run, playerId, change, TestRuns.NOW + 1);
    }

    @Test
    @DisplayName("a change moves one participant and leaves the rest of the party alone")
    void oneParticipantAtATime() {
        PersistedRun run = twoPlayers();

        PersistedRun next = change(run, ONE, ParticipantState::knockedOut);

        assertEquals(CombatState.KNOCKED_OUT, stateOf(next, ONE).combat());
        assertEquals(ParticipantState.joined(), stateOf(next, TWO),
                "the other player was not in this change and should not have moved");
        assertEquals(TestRuns.NOW + 1, next.updatedAt());
    }

    @Test
    @DisplayName("a disconnect keeps the combat state, and a reconnect comes back to it")
    void disconnectPreservesCombatState() {
        // The whole reason P1 separated the axes: one enum would have to forget one of these facts,
        // and a reconnect would then have to guess which.
        PersistedRun run = change(twoPlayers(), ONE, ParticipantState::knockedOut);

        PersistedRun gone = change(run, ONE, ParticipantState::disconnected);
        assertEquals(ConnectionState.DISCONNECTED, stateOf(gone, ONE).connection());
        assertEquals(CombatState.KNOCKED_OUT, stateOf(gone, ONE).combat(),
                "a disconnect is not a knockout being undone");

        PersistedRun back = change(gone, ONE, ParticipantState::reconnected);
        assertEquals(ConnectionState.ONLINE, stateOf(back, ONE).connection());
        assertEquals(CombatState.KNOCKED_OUT, stateOf(back, ONE).combat(),
                "they come back to exactly what they left");
    }

    @Test
    @DisplayName("leaving is terminal: nothing later puts a player back into the run")
    void leavingIsTerminal() {
        PersistedRun left = change(twoPlayers(), ONE, ParticipantState::left);
        assertEquals(MembershipState.VOLUNTARILY_LEFT, stateOf(left, ONE).membership());

        // Every path that could quietly return them: an intermission, a reconnect, a revive.
        PersistedRun after = ParticipantService.revivedAtIntermission(
                change(change(left, ONE, ParticipantState::reconnected), ONE, ParticipantState::revived),
                TestRuns.NOW + 2);

        assertEquals(MembershipState.VOLUNTARILY_LEFT, stateOf(after, ONE).membership());
        assertFalse(stateOf(after, ONE).canFight(), "a player who left cannot be put back into a fight");
        assertFalse(stateOf(after, ONE).isInRun());
    }

    @Test
    @DisplayName("a change that changes nothing returns the very same run, so nothing is written")
    void unchangedRunIsNotRewritten() {
        PersistedRun run = twoPlayers();

        assertSame(run, change(run, ONE, ParticipantState::reconnected), "they were already online");
        assertSame(run, change(run, UUID.randomUUID(), ParticipantState::knockedOut),
                "a player who is not in this run cannot move anything in it");
        assertSame(run, ParticipantService.revivedAtIntermission(run, TestRuns.NOW + 1),
                "nobody is out, so an intermission has nobody to revive");
    }

    @Test
    @DisplayName("the knockout cycle: out, watching, owed the intermission, back in the fight")
    void knockoutCycle() {
        PersistedRun run = twoPlayers();

        PersistedRun out = change(change(run, ONE, ParticipantState::knockedOut), ONE, ParticipantState::spectating);
        assertTrue(stateOf(out, ONE).isSpectating());
        assertFalse(stateOf(out, ONE).canFight());

        PersistedRun owed = ParticipantService.pendingRevival(out, TestRuns.NOW + 2);
        assertEquals(CombatState.REVIVE_PENDING, stateOf(owed, ONE).combat());
        assertEquals(CombatState.ACTIVE, stateOf(owed, TWO).combat(),
                "a player who never went out is not waiting on anything");
        assertFalse(stateOf(owed, ONE).canFight(), "the promise is the intermission, not this moment");

        PersistedRun back = ParticipantService.revivedAtIntermission(owed, TestRuns.NOW + 3);
        assertEquals(CombatState.ACTIVE, stateOf(back, ONE).combat());
        assertTrue(stateOf(back, ONE).canFight());
    }

    @Test
    @DisplayName("an intermission also takes a spectator nothing ever marked as pending")
    void intermissionTakesPlainSpectators() {
        // A floor can end without passing through the mark: a boss aborted, a run resumed. Without
        // this, that player spectates a floor that is no longer being fought, for ever.
        PersistedRun out = change(twoPlayers(), ONE, ParticipantState::knockedOut);

        PersistedRun back = ParticipantService.revivedAtIntermission(out, TestRuns.NOW + 2);

        assertEquals(CombatState.ACTIVE, stateOf(back, ONE).combat());
    }

    @Test
    @DisplayName("an intermission revives a disconnected player's combat state but does not bring them back")
    void intermissionDoesNotFakePresence() {
        PersistedRun run = change(change(twoPlayers(), ONE, ParticipantState::knockedOut),
                ONE, ParticipantState::disconnected);

        PersistedRun back = ParticipantService.revivedAtIntermission(run, TestRuns.NOW + 2);

        assertEquals(CombatState.ACTIVE, stateOf(back, ONE).combat(), "their place in the run is restored");
        assertEquals(ConnectionState.DISCONNECTED, stateOf(back, ONE).connection());
        assertFalse(stateOf(back, ONE).canFight(), "being owed a revival is not being here");
    }

    @Test
    @DisplayName("a run counts as empty only when nobody in it is online")
    void nobodyLeft() {
        PersistedRun run = twoPlayers();
        assertFalse(ParticipantService.nobodyLeft(run));

        PersistedRun oneGone = change(run, ONE, ParticipantState::disconnected);
        assertFalse(ParticipantService.nobodyLeft(oneGone), "one player is still playing it");

        assertTrue(ParticipantService.nobodyLeft(change(oneGone, TWO, ParticipantState::left)));
    }
}
