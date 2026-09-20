package com.cobbletowers.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The index: what it answers, and what it must stop answering. */
class TowerRunsTest {

    private static final UUID RUN = UUID.fromString("ffffffff-0000-0000-0000-000000000006");
    private static final UUID OTHER_RUN = UUID.fromString("ffffffff-0000-0000-0000-000000000007");
    private static final UUID PLAYER = TestRuns.PLAYER;

    @AfterEach
    void clear() {
        TowerRuns.onServerStopped();
    }

    @Test
    @DisplayName("a player in a run is found by their own id, not by searching every run")
    void playerIndex() {
        TowerRuns.resetForTests(List.of(TestRuns.fresh(RUN)));

        assertEquals(Optional.of(RUN), TowerRuns.forPlayer(PLAYER).map(PersistedRun::runId));
        assertTrue(TowerRuns.forPlayer(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("a finished run stops holding its players, so they can start another")
    void finishedRunReleasesPlayers() {
        TowerRuns.resetForTests(List.of(TestRuns.at(RUN, RunState.COMPLETED, TestRuns.NOW)));

        assertTrue(TowerRuns.forPlayer(PLAYER).isEmpty(),
                "a completed run must not keep someone out of the next one");
        assertEquals(1, TowerRuns.all().size(), "though it is still there to be read");
    }

    @Test
    @DisplayName("someone who left a run stops being one of its players")
    void leavingReleasesThePlayer() {
        PersistedRun run = TestRuns.fresh(RUN);
        PersistedRun afterLeaving = new PersistedRun(run.runId(), run.schemaVersion(), run.towerId(),
                run.towerRevision(), run.towerDigest(), run.rulesetRevision(), run.structureRevision(), run.seed(),
                run.floorIndex(), run.state(),
                List.of(new PersistedParticipant(PLAYER, ParticipantState.joined().left(), List.of())),
                run.lastCheckpoint(), run.committedTransactions(), run.updatedAt(), run.cell(), run.ledger(),
                run.modifiers(), run.lastBankedFloor(), run.vendorPurchases());

        TowerRuns.resetForTests(List.of(afterLeaving));

        assertTrue(TowerRuns.forPlayer(PLAYER).isEmpty());
    }

    @Test
    @DisplayName("a player already in a newer run is not released by an older one being indexed")
    void anOlderRunDoesNotStealTheMapping() {
        // Removal is by value for this reason: re-indexing a finished run must not detach a player
        // from the run they are actually in now.
        TowerRuns.resetForTests(List.of(
                TestRuns.fresh(OTHER_RUN),
                TestRuns.at(RUN, RunState.COMPLETED, TestRuns.NOW)));

        assertEquals(Optional.of(OTHER_RUN), TowerRuns.forPlayer(PLAYER).map(PersistedRun::runId));
    }

    @Test
    @DisplayName("shutdown empties the index, because these statics outlive a world")
    void shutdownClears() {
        TowerRuns.resetForTests(List.of(TestRuns.fresh(RUN)));

        assertEquals(1, TowerRuns.onServerStopped());

        assertTrue(TowerRuns.all().isEmpty());
        assertTrue(TowerRuns.get(RUN).isEmpty());
        assertFalse(TowerRuns.forPlayer(PLAYER).isPresent());
    }
}
