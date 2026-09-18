package com.cobbletowers.runtime;

import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.RunState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Every legal move a run can make, as data.
 *
 * <p>The TDS gives state names and arrows; this adds what an implementation actually needs -- which
 * event causes each move, where a checkpoint is forced, what idempotency key that checkpoint commits
 * under, and how a run leaves RECOVERY_REQUIRED. Being data rather than a switch statement means the
 * whole machine is testable without a server, and the invariants are checks rather than intentions.
 *
 * <p>Two events are accepted from any live state: ABANDON_REQUESTED, because a party may always stop,
 * and TECHNICAL_FAILURE, because anything can break. Neither is ever reported as a player loss
 * (TDS §8).
 */
public final class RunTransitions {

    /**
     * One move.
     *
     * <p>A checkpoint and a key are two different things, and P3 is where the difference started to
     * matter. A checkpoint is durability: write this now, because a crash after it must not undo it.
     * A key is idempotency of a commit that carries value (TDS #30). Every keyed move checkpoints,
     * but not every checkpointing move needs a key -- and the two that can legitimately happen more
     * than once to the same run, abandoning and breaking, deliberately carry none. Keyed moves happen
     * at most once per run and floor, so a repeated key means two outcomes under one identity, which
     * is what the service refuses.
     *
     * @param checkpoint          whether the run must be persisted before the side effect runs
     * @param keyTemplate         the idempotency key this commits under, with {run}, {floor} and
     *                            {nextFloor} placeholders; empty when the move commits no value
     * @param resumeFromCheckpoint true only for recovery, where the next state comes from the
     *                            checkpoint rather than from this table
     */
    public record Transition(RunState next, boolean checkpoint, String keyTemplate, boolean resumeFromCheckpoint) {
        public Transition {
            Objects.requireNonNull(next, "next");
            Objects.requireNonNull(keyTemplate, "keyTemplate");
            if (!keyTemplate.isEmpty() && !checkpoint) {
                throw new IllegalArgumentException("an idempotency key without a checkpoint commits nothing");
            }
        }

        /**
         * The concrete key for one run and floor.
         *
         * <p>{@code floorIndex} is the floor the run is on <em>before</em> this move is applied.
         * NEXT_FLOOR_CONFIRMED is the reason it matters: its key says {@code {nextFloor}}, so a
         * caller that increments the floor first would commit the floor after next under a key the
         * previous floor already used.
         */
        public String key(UUID runId, int floorIndex) {
            return keyTemplate
                    .replace("{run}", runId.toString())
                    .replace("{floor}", Integer.toString(floorIndex))
                    .replace("{nextFloor}", Integer.toString(floorIndex + 1));
        }
    }

    private static final Map<RunState, Map<RunEvent, Transition>> TABLE = new EnumMap<>(RunState.class);

    static {
        put(RunState.CREATED, RunEvent.PARTY_SUBMITTED, RunState.VALIDATING_PARTY);
        put(RunState.VALIDATING_PARTY, RunEvent.PARTY_VALIDATED, RunState.ALLOCATING_INSTANCE);
        put(RunState.VALIDATING_PARTY, RunEvent.PARTY_REJECTED, RunState.ABANDONED);
        checkpoint(RunState.ALLOCATING_INSTANCE, RunEvent.INSTANCE_ALLOCATED, RunState.PREPARING, "run:{run}:allocated");
        put(RunState.ALLOCATING_INSTANCE, RunEvent.ALLOCATION_FAILED, RunState.RECOVERY_REQUIRED);
        checkpoint(RunState.PREPARING, RunEvent.PREPARATION_COMPLETE, RunState.FLOOR_READY, "run:{run}:floor:{floor}:ready");
        checkpoint(RunState.FLOOR_READY, RunEvent.ENCOUNTER_STARTED, RunState.ENCOUNTER_ACTIVE, "run:{run}:floor:{floor}:encounter");
        checkpoint(RunState.ENCOUNTER_ACTIVE, RunEvent.ENCOUNTER_RESOLVED_CLEARED, RunState.FLOOR_RESOLVING, "run:{run}:floor:{floor}:resolved");
        checkpoint(RunState.ENCOUNTER_ACTIVE, RunEvent.ENCOUNTER_RESOLVED_WIPED, RunState.FAILED, "run:{run}:wiped");
        checkpoint(RunState.FLOOR_RESOLVING, RunEvent.REWARDS_BANKED, RunState.INTERMISSION, "run:{run}:floor:{floor}:banked");
        checkpoint(RunState.FLOOR_RESOLVING, RunEvent.FINAL_FLOOR_CLEARED, RunState.COMPLETED, "run:{run}:completed");
        checkpoint(RunState.INTERMISSION, RunEvent.INTERMISSION_COMPLETE, RunState.NEXT_FLOOR_READY, "run:{run}:floor:{floor}:intermission");
        checkpoint(RunState.INTERMISSION, RunEvent.CASH_OUT_CHOSEN, RunState.CASHED_OUT, "run:{run}:cashed_out");
        checkpoint(RunState.NEXT_FLOOR_READY, RunEvent.NEXT_FLOOR_CONFIRMED, RunState.FLOOR_READY, "run:{run}:floor:{nextFloor}:ready");
        // Recovery: the next state is whatever the last checkpoint recorded, so the table cannot name it.
        TABLE.computeIfAbsent(RunState.RECOVERY_REQUIRED, state -> new EnumMap<>(RunEvent.class))
                .put(RunEvent.RECOVERY_COMPLETED, new Transition(RunState.RECOVERY_REQUIRED, true, "", true));
        checkpoint(RunState.RECOVERY_REQUIRED, RunEvent.RECOVERY_ABANDONED, RunState.ABANDONED, "");
    }

    private RunTransitions() {}

    private static void put(RunState from, RunEvent event, RunState to) {
        requireNotWildcard(event);
        TABLE.computeIfAbsent(from, state -> new EnumMap<>(RunEvent.class))
                .put(event, new Transition(to, false, "", false));
    }

    private static void checkpoint(RunState from, RunEvent event, RunState to, String keyTemplate) {
        requireNotWildcard(event);
        TABLE.computeIfAbsent(from, state -> new EnumMap<>(RunEvent.class))
                .put(event, new Transition(to, true, keyTemplate, false));
    }

    /**
     * Refuses a table entry for an event {@link #lookup} answers before it ever reads the table.
     *
     * <p>Package-private so a test can pin it. The two wildcards are checked first so that a party
     * can always stop and a fault is never a loss, which means a state-specific entry for either
     * would be written, compile, and never once be read. Failing here -- loudly, while the class
     * initializes -- is the only way that mistake gets noticed.
     */
    static void requireNotWildcard(RunEvent event) {
        if (event == RunEvent.ABANDON_REQUESTED || event == RunEvent.TECHNICAL_FAILURE) {
            throw new IllegalArgumentException(
                    event + " is answered for every live state by lookup; a table entry for it would be shadowed."
                            + " Change lookup's wildcard instead.");
        }
    }

    /** The move {@code event} causes from {@code state}, or empty when it is not legal there. */
    public static Optional<Transition> lookup(RunState state, RunEvent event) {
        if (state.isLive()) {
            if (event == RunEvent.ABANDON_REQUESTED) {
                return Optional.of(new Transition(RunState.ABANDONED, true, "", false));
            }
            if (event == RunEvent.TECHNICAL_FAILURE) {
                return Optional.of(new Transition(RunState.RECOVERY_REQUIRED, true, "", false));
            }
        }
        Map<RunEvent, Transition> byEvent = TABLE.get(state);
        return byEvent == null ? Optional.empty() : Optional.ofNullable(byEvent.get(event));
    }

    /** Every event legal from {@code state}, wildcards included. For tests and diagnostics. */
    public static List<RunEvent> eventsFrom(RunState state) {
        List<RunEvent> events = new ArrayList<>();
        for (RunEvent event : RunEvent.values()) {
            if (lookup(state, event).isPresent()) events.add(event);
        }
        return List.copyOf(events);
    }

    /** One line per legal move, for the debug command and for reading the machine in a log. */
    public static List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (RunState state : RunState.values()) {
            for (RunEvent event : eventsFrom(state)) {
                Transition transition = lookup(state, event).orElseThrow();
                String target = transition.resumeFromCheckpoint() ? "<last checkpoint>" : transition.next().name();
                lines.add(String.format(Locale.ROOT, "%s + %s -> %s%s", state, event, target,
                        transition.checkpoint() ? " [checkpoint " + transition.keyTemplate() + "]" : ""));
            }
        }
        return List.copyOf(lines);
    }
}
