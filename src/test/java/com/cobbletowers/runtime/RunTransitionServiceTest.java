package com.cobbletowers.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.RunCheckpoint;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The decision half of the service, which is all of the behaviour and none of the server.
 */
class RunTransitionServiceTest {

    private static final UUID RUN = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private static PersistedRun move(PersistedRun run, RunEvent event, long now) {
        RunTransitionService.Outcome outcome = RunTransitionService.decide(run, event, now);
        return assertInstanceOf(RunTransitionService.Move.class, outcome,
                event + " from " + run.state() + " should be legal").next();
    }

    private static RunTransitionService.Refusal refusal(PersistedRun run, RunEvent event) {
        return assertInstanceOf(RunTransitionService.Refusal.class,
                RunTransitionService.decide(run, event, TestRuns.NOW));
    }

    @Test
    @DisplayName("a legal move advances the state and stamps the time")
    void legalMove() {
        PersistedRun run = TestRuns.fresh(RUN);

        PersistedRun next = move(run, RunEvent.PARTY_SUBMITTED, TestRuns.NOW + 5);

        assertEquals(RunState.VALIDATING_PARTY, next.state());
        assertEquals(TestRuns.NOW + 5, next.updatedAt());
        assertEquals(run.floorIndex(), next.floorIndex(), "only NEXT_FLOOR_CONFIRMED changes the floor");
    }

    @Test
    @DisplayName("an event with no move from here is refused, and the refusal says what is legal")
    void illegalEvent() {
        RunTransitionService.Refusal refused = refusal(TestRuns.fresh(RUN), RunEvent.REWARDS_BANKED);

        assertEquals(RunTransitionService.Reason.ILLEGAL_EVENT, refused.reason());
        assertTrue(refused.detail().contains("PARTY_SUBMITTED"),
                "a refusal a person reads should name the way out: " + refused.detail());
    }

    @Test
    @DisplayName("replaying a move that already happened is refused, because the state moved")
    void replayIsRefusedByTheMachineItself() {
        PersistedRun run = TestRuns.fresh(RUN);
        PersistedRun once = move(run, RunEvent.PARTY_SUBMITTED, TestRuns.NOW);

        // This is why there is no "already applied" success: applying a move moves the state, so the
        // second attempt finds no transition at all.
        assertEquals(RunTransitionService.Reason.ILLEGAL_EVENT, refusal(once, RunEvent.PARTY_SUBMITTED).reason());
    }

    @Test
    @DisplayName("a checkpointing move records the key and the state to come back to")
    void checkpointRecorded() {
        PersistedRun run = walkTo(RunState.ALLOCATING_INSTANCE);

        RunTransitionService.Outcome outcome =
                RunTransitionService.decide(run, RunEvent.INSTANCE_ALLOCATED, TestRuns.NOW);
        RunTransitionService.Move move = assertInstanceOf(RunTransitionService.Move.class, outcome);

        assertTrue(move.checkpoint(), "allocation is one of the moves the table forces a write for");
        assertEquals("run:" + RUN + ":allocated", move.key());
        assertEquals(java.util.Optional.of(new RunCheckpoint(move.key(), RunState.PREPARING)),
                move.next().lastCheckpoint());
        assertTrue(move.next().hasCommitted(move.key()), "the key is recorded so a later commit can see it");
    }

    @Test
    @DisplayName("a non-checkpointing move leaves the previous checkpoint alone")
    void nonCheckpointKeepsTheOldCheckpoint() {
        PersistedRun prepared = walkTo(RunState.PREPARING);
        assertTrue(prepared.lastCheckpoint().isPresent());

        // VALIDATING_PARTY -> ALLOCATING_INSTANCE does not checkpoint; the run must still remember
        // where it was last committed, or a crash would have nothing to come back to.
        PersistedRun run = TestRuns.fresh(RUN);
        PersistedRun submitted = move(run, RunEvent.PARTY_SUBMITTED, TestRuns.NOW);
        PersistedRun validated = move(submitted, RunEvent.PARTY_VALIDATED, TestRuns.NOW);

        assertEquals(submitted.lastCheckpoint(), validated.lastCheckpoint());
    }

    @Test
    @DisplayName("confirming the next floor is the one move that changes the floor")
    void floorAdvances() {
        PersistedRun ready = walkTo(RunState.NEXT_FLOOR_READY);
        int floor = ready.floorIndex();

        RunTransitionService.Move move = assertInstanceOf(RunTransitionService.Move.class,
                RunTransitionService.decide(ready, RunEvent.NEXT_FLOOR_CONFIRMED, TestRuns.NOW));

        assertEquals(floor + 1, move.next().floorIndex());
        assertEquals(RunState.FLOOR_READY, move.next().state());
        assertEquals("run:" + RUN + ":floor:" + (floor + 1) + ":ready", move.key(),
                "the key names the floor being opened, which is why it is built before the floor changes");
    }

    @Test
    @DisplayName("committing a key twice is refused rather than written")
    void keyReuseIsRefused() {
        PersistedRun ready = walkTo(RunState.NEXT_FLOOR_READY);
        PersistedRun opened = move(ready, RunEvent.NEXT_FLOOR_CONFIRMED, TestRuns.NOW);

        // Put the run back a floor while keeping what it has committed: the same key would be built
        // a second time. Two outcomes under one key is the thing the key exists to prevent, so it is
        // reported rather than absorbed.
        PersistedRun rewound = new PersistedRun(opened.runId(), opened.schemaVersion(), opened.towerId(),
                opened.towerRevision(), opened.towerDigest(), opened.rulesetRevision(), opened.structureRevision(),
                opened.seed(), opened.floorIndex() - 1, RunState.NEXT_FLOOR_READY, opened.participants(),
                opened.lastCheckpoint(), opened.committedTransactions(), opened.updatedAt());

        assertEquals(RunTransitionService.Reason.KEY_REUSED,
                refusal(rewound, RunEvent.NEXT_FLOOR_CONFIRMED).reason());
    }

    @Test
    @DisplayName("a run can always be abandoned or break, and a broken one parks rather than losing")
    void wildcards() {
        PersistedRun active = walkTo(RunState.ENCOUNTER_ACTIVE);

        assertEquals(RunState.ABANDONED, move(active, RunEvent.ABANDON_REQUESTED, TestRuns.NOW).state());
        assertEquals(RunState.RECOVERY_REQUIRED, move(active, RunEvent.TECHNICAL_FAILURE, TestRuns.NOW).state(),
                "a technical failure is never reported as a player loss");
    }

    @Test
    @DisplayName("resuming is refused for now, and says why rather than pretending")
    void resumeIsNotYetPossible() {
        PersistedRun parked = move(walkTo(RunState.ENCOUNTER_ACTIVE), RunEvent.TECHNICAL_FAILURE, TestRuns.NOW);
        assertEquals(RunState.RECOVERY_REQUIRED, parked.state());

        RunTransitionService.Refusal refused = refusal(parked, RunEvent.RECOVERY_COMPLETED);

        assertEquals(RunTransitionService.Reason.NOT_RESUMABLE_YET, refused.reason());
        assertTrue(parked.lastCheckpoint().isPresent(), "what a resume will need is kept, even though it is refused");
    }

    @Test
    @DisplayName("a parked run can still be given up on")
    void parkedRunCanBeAbandoned() {
        PersistedRun parked = move(walkTo(RunState.ENCOUNTER_ACTIVE), RunEvent.TECHNICAL_FAILURE, TestRuns.NOW);

        assertEquals(RunState.ABANDONED, move(parked, RunEvent.RECOVERY_ABANDONED, TestRuns.NOW).state());
    }

    /** Walks a fresh run up to {@code target} through legal moves only. */
    private static PersistedRun walkTo(RunState target) {
        PersistedRun run = TestRuns.fresh(RUN);
        for (RunEvent event : new RunEvent[]{RunEvent.PARTY_SUBMITTED, RunEvent.PARTY_VALIDATED,
                RunEvent.INSTANCE_ALLOCATED, RunEvent.PREPARATION_COMPLETE, RunEvent.ENCOUNTER_STARTED,
                RunEvent.ENCOUNTER_RESOLVED_CLEARED, RunEvent.REWARDS_BANKED, RunEvent.INTERMISSION_COMPLETE}) {
            if (run.state() == target) return run;
            run = move(run, event, TestRuns.NOW);
        }
        assertEquals(target, run.state(), "the walk should reach " + target);
        return run;
    }
}
