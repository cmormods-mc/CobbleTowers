package com.cobbletowers.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.persistence.PersistedRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a run pins when it starts, which is what tells an edited tower from a missing one later. */
class RunFactoryTest {

    @Test
    @DisplayName("a new run starts on floor one with the content it will be played against pinned")
    void createPinsContent() {
        PersistedRun run = RunFactory.create(TestRuns.content(), TestRuns.TOWER, List.of(TestRuns.PLAYER),
                1234L, TestRuns.NOW).orElseThrow();

        assertEquals(RunState.CREATED, run.state());
        assertEquals(1, run.floorIndex(), "towers number their floors from one, and so does a run");
        assertEquals(3, run.towerRevision());
        assertEquals(5, run.rulesetRevision(), "the ruleset is versioned separately from the tower");
        assertEquals("digest-abc", run.towerDigest());
        assertEquals(1234L, run.seed(), "an interrupted encounter is rebuilt from this, never rerolled");
        assertEquals(Optional.empty(), run.lastCheckpoint());
        assertEquals(List.of(), run.committedTransactions());
        assertEquals(TestRuns.NOW, run.updatedAt());
    }

    @Test
    @DisplayName("every player is a member from the start, with no party captured yet")
    void participants() {
        UUID second = UUID.fromString("22222222-0000-0000-0000-000000000008");
        PersistedRun run = RunFactory.create(TestRuns.content(), TestRuns.TOWER,
                List.of(TestRuns.PLAYER, second), 1L, TestRuns.NOW).orElseThrow();

        assertEquals(2, run.participants().size());
        assertTrue(run.participants().stream().allMatch(participant -> participant.state().canFight()));
        assertTrue(run.participants().stream().allMatch(participant -> participant.registeredPokemon().isEmpty()),
                "the party is captured when it is validated, which is not this phase's job");
    }

    @Test
    @DisplayName("a tower nobody loaded produces no run")
    void unknownTower() {
        assertTrue(RunFactory.create(TestRuns.content(), TestRuns.id("nonexistent"), List.of(TestRuns.PLAYER),
                1L, TestRuns.NOW).isEmpty());
    }

    @Test
    @DisplayName("two runs on the same tower are different runs")
    void idsAreDistinct() {
        PersistedRun first = RunFactory.create(TestRuns.content(), TestRuns.TOWER, List.of(TestRuns.PLAYER),
                1L, TestRuns.NOW).orElseThrow();
        PersistedRun second = RunFactory.create(TestRuns.content(), TestRuns.TOWER, List.of(TestRuns.PLAYER),
                1L, TestRuns.NOW).orElseThrow();

        assertTrue(!first.runId().equals(second.runId()));
    }
}
