package com.cobbletowers.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.persistence.PersistedRun;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The in-fight overlay must be told the moment a run's modifiers, relics, floor or life change, and never for nothing. */
class RunEffectsChangeTest {

    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("a run seen for the first time is pushed")
    void firstSight() {
        assertTrue(RunEffectsService.changed(null, TestRuns.at(ID, RunState.CREATED, TestRuns.NOW)));
    }

    @Test
    @DisplayName("saving a run that changed nothing the overlay shows pushes nothing")
    void sameRun() {
        PersistedRun run = TestRuns.at(ID, RunState.ENCOUNTER_ACTIVE, TestRuns.NOW);
        PersistedRun later = TestRuns.at(ID, RunState.ENCOUNTER_ACTIVE, TestRuns.NOW + 5000);
        assertFalse(RunEffectsService.changed(run, later));
    }

    @Test
    @DisplayName("a modifier taken, a relic found or one lost is pushed")
    void modifiersAndRelics() {
        PersistedRun run = TestRuns.at(ID, RunState.INTERMISSION, TestRuns.NOW);
        PersistedRun taken = TestRuns.holding(run, TestRuns.id("hard"));
        assertTrue(RunEffectsService.changed(run, taken));
        PersistedRun relic = taken.withModifiers(taken.modifiers().withRelic(TestRuns.id("lucky"), 4), TestRuns.NOW);
        assertTrue(RunEffectsService.changed(taken, relic));
        PersistedRun lost = relic.withModifiers(relic.modifiers().withoutRelic(TestRuns.id("lucky")), TestRuns.NOW);
        assertTrue(RunEffectsService.changed(relic, lost));
    }

    @Test
    @DisplayName("the run ending is pushed, so the overlay forgets it")
    void ending() {
        PersistedRun live = TestRuns.at(ID, RunState.ENCOUNTER_ACTIVE, TestRuns.NOW);
        PersistedRun over = TestRuns.at(ID, RunState.FAILED, TestRuns.NOW);
        assertTrue(RunEffectsService.changed(live, over));
    }
}
