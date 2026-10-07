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
 * Every legal move a run can make, as data, so the machine and its invariants are testable without a server.
 * ABANDON_REQUESTED and TECHNICAL_FAILURE are accepted from any live state and are never a player loss (TDS section
 * 8).
 */
public final class RunTransitions {

    /**
     * One move. A checkpoint is durability (write now); a key is idempotency of a value-carrying commit (TDS #30).
     * Keyed moves checkpoint; abandoning and breaking checkpoint without a key because they can repeat.
     * @param checkpoint whether the run is persisted before the side effect
     * @param keyTemplate the key with {run}, {floor} and {nextFloor} placeholders; empty when no value is committed
     * @param resumeFromCheckpoint true only for recovery, where the next state comes from the checkpoint
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
         * The concrete key for one run and floor. {@code floorIndex} is the floor before this move is applied, which
         * matters for {@code {nextFloor}}.
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
     * Refuses a table entry for an event {@link #lookup} answers before reading the table (the two wildcards),
     * failing loudly at class init. Package-private so a test can pin it.
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
