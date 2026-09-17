package com.cobbletowers.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.RunState;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The state machine as properties rather than examples: a table nobody can walk out of, and no state
 * nobody can leave.
 */
class RunTransitionsTest {

    @Test
    @DisplayName("every live state can be left, and no terminal state can")
    void reachability() {
        for (RunState state : RunState.values()) {
            List<RunEvent> events = RunTransitions.eventsFrom(state);
            if (state.isTerminal()) {
                assertTrue(events.isEmpty(), state + " is terminal but accepts " + events);
            } else {
                assertFalse(events.isEmpty(), state + " has no way out");
            }
        }
    }

    @Test
    @DisplayName("every state and every event is used somewhere")
    void nothingIsDeadWeight() {
        Set<RunState> reached = EnumSet.of(RunState.CREATED);  // where a run begins
        Set<RunEvent> used = EnumSet.noneOf(RunEvent.class);
        for (RunState state : RunState.values()) {
            for (RunEvent event : RunTransitions.eventsFrom(state)) {
                used.add(event);
                RunTransitions.Transition transition = RunTransitions.lookup(state, event).orElseThrow();
                if (!transition.resumeFromCheckpoint()) reached.add(transition.next());
            }
        }
        assertEquals(EnumSet.allOf(RunState.class), reached, "a state nothing leads to cannot happen");
        assertEquals(EnumSet.allOf(RunEvent.class), used, "an event no state accepts can never be applied");
    }

    @Test
    @DisplayName("a run can always be abandoned or fail technically, and never from a terminal state")
    void wildcards() {
        for (RunState state : RunState.values()) {
            boolean abandonable = RunTransitions.lookup(state, RunEvent.ABANDON_REQUESTED).isPresent();
            boolean breakable = RunTransitions.lookup(state, RunEvent.TECHNICAL_FAILURE).isPresent();
            if (state.isLive()) {
                assertTrue(abandonable, state + " cannot be abandoned");
                assertTrue(breakable, state + " cannot report a technical failure");
                assertEquals(RunState.RECOVERY_REQUIRED,
                        RunTransitions.lookup(state, RunEvent.TECHNICAL_FAILURE).orElseThrow().next(),
                        "a technical failure is never a player loss");
            } else {
                assertFalse(abandonable, state + " is not live but accepts abandonment");
                assertFalse(breakable, state + " is not live but accepts a technical failure");
            }
        }
    }

    @Test
    @DisplayName("recovery resumes from the checkpoint, and can also be given up on")
    void recovery() {
        RunTransitions.Transition resumed =
                RunTransitions.lookup(RunState.RECOVERY_REQUIRED, RunEvent.RECOVERY_COMPLETED).orElseThrow();
        assertTrue(resumed.resumeFromCheckpoint(), "the table cannot name the state; the checkpoint does");

        assertEquals(RunState.ABANDONED,
                RunTransitions.lookup(RunState.RECOVERY_REQUIRED, RunEvent.RECOVERY_ABANDONED).orElseThrow().next());
    }

    @Test
    @DisplayName("every checkpointing move names a key that identifies its run")
    void checkpointsCarryKeys() {
        UUID run = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        List<String> keys = new ArrayList<>();
        for (RunState state : RunState.values()) {
            for (RunEvent event : RunTransitions.eventsFrom(state)) {
                RunTransitions.Transition transition = RunTransitions.lookup(state, event).orElseThrow();
                if (!transition.checkpoint()) continue;
                String key = transition.key(run, 3);
                assertTrue(key.contains(run.toString()), key + " does not identify its run");
                assertFalse(key.contains("{"), key + " left a placeholder unfilled");
                keys.add(key);
            }
        }
        assertFalse(keys.isEmpty(), "no transition forces a checkpoint");
    }

    @Test
    @DisplayName("two moves share an idempotency key only when they commit the same outcome")
    void keysCollideOnlyOnTheSameOutcome() {
        UUID run = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        Map<String, RunState> committedBy = new HashMap<>();
        for (RunState state : RunState.values()) {
            for (RunEvent event : RunTransitions.eventsFrom(state)) {
                RunTransitions.Transition transition = RunTransitions.lookup(state, event).orElseThrow();
                if (!transition.checkpoint()) continue;
                String key = transition.key(run, 3);
                RunState already = committedBy.putIfAbsent(key, transition.next());
                if (already != null) {
                    assertEquals(already, transition.next(),
                            key + " commits two different outcomes, so replaying it is not safe");
                }
            }
        }
        // Abandoning is deliberately one key from many states: a run is abandoned once, however it
        // got there. If that stops being the only collision, the assertion above is the guard.
        assertEquals(RunState.ABANDONED, committedBy.get("run:" + run + ":abandoned"));
    }

    @Test
    @DisplayName("a floor-scoped key names the floor it belongs to")
    void floorKeysVaryByFloor() {
        UUID run = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        Set<String> floorScoped = new HashSet<>();
        for (RunState state : RunState.values()) {
            for (RunEvent event : RunTransitions.eventsFrom(state)) {
                RunTransitions.Transition transition = RunTransitions.lookup(state, event).orElseThrow();
                if (!transition.keyTemplate().contains("{floor}")
                        && !transition.keyTemplate().contains("{nextFloor}")) continue;
                assertNotEquals(transition.key(run, 3), transition.key(run, 4),
                        transition.keyTemplate() + " commits two floors under one key");
                floorScoped.add(transition.keyTemplate());
            }
        }
        // Named rather than counted, so adding or dropping a floor-scoped move fails by name.
        assertEquals(Set.of("run:{run}:floor:{floor}:ready",
                        "run:{run}:floor:{floor}:encounter",
                        "run:{run}:floor:{floor}:resolved",
                        "run:{run}:floor:{floor}:banked",
                        "run:{run}:floor:{floor}:intermission",
                        "run:{run}:floor:{nextFloor}:ready"),
                floorScoped, "the moves scoped to a floor are the ones the design table lists");
    }

    @Test
    @DisplayName("an event the wildcard already answers cannot be put in the table")
    void wildcardEventsAreRefusedFromTheTable() {
        for (RunEvent shadowed : List.of(RunEvent.ABANDON_REQUESTED, RunEvent.TECHNICAL_FAILURE)) {
            assertThrows(IllegalArgumentException.class,
                    () -> RunTransitions.requireNotWildcard(shadowed),
                    shadowed + " would be written into the table and never read");
        }
        RunTransitions.requireNotWildcard(RunEvent.PARTY_SUBMITTED);  // an ordinary event is fine
    }

    @Test
    @DisplayName("a clean run walks the floors it is meant to")
    void happyPath() {
        RunState state = RunState.CREATED;
        for (RunEvent event : List.of(RunEvent.PARTY_SUBMITTED, RunEvent.PARTY_VALIDATED,
                RunEvent.INSTANCE_ALLOCATED, RunEvent.PREPARATION_COMPLETE, RunEvent.ENCOUNTER_STARTED,
                RunEvent.ENCOUNTER_RESOLVED_CLEARED, RunEvent.REWARDS_BANKED, RunEvent.INTERMISSION_COMPLETE,
                RunEvent.NEXT_FLOOR_CONFIRMED)) {
            state = RunTransitions.lookup(state, event).orElseThrow(() ->
                    new AssertionError("no transition")).next();
        }
        assertEquals(RunState.FLOOR_READY, state, "the second floor begins where the first did");

        // ...and the last floor ends the run rather than looping.
        RunState end = RunTransitions.lookup(RunState.FLOOR_RESOLVING, RunEvent.FINAL_FLOOR_CLEARED)
                .orElseThrow().next();
        assertEquals(RunState.COMPLETED, end);
        assertTrue(end.isTerminal());
    }
}
