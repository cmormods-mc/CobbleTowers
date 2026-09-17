package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.RunCheckpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * The only thing that moves a run.
 *
 * <p>Split in two on purpose. {@link #decide} is pure -- a run, an event and a clock reading in, the
 * next record or a refusal out -- so the whole machine is testable without a server. {@link #apply}
 * is the thin part that writes what was decided.
 *
 * <p>Which moves force a checkpoint is not restated here: {@code Transition.checkpoint()} and
 * {@code keyTemplate} already carry that, so the policy has one home and this reads it.
 */
public final class RunTransitionService {

    /** What an event does to a run. */
    public sealed interface Outcome permits Move, Refusal {}

    /**
     * The run as it will be, and how it must be written.
     *
     * @param key the idempotency key when this move checkpoints, otherwise empty
     */
    public record Move(PersistedRun next, RunState from, boolean checkpoint, String key) implements Outcome {}

    public record Refusal(Reason reason, String detail) implements Outcome {}

    public enum Reason {
        UNKNOWN_RUN,
        /** The table has no move for this event from this state. Includes replaying a move already made. */
        ILLEGAL_EVENT,
        /** Recovery resumes into an instance, and instances arrive in P3. */
        NOT_RESUMABLE_YET,
        /** A checkpoint key already committed is being committed again for a different outcome. */
        KEY_REUSED
    }

    private RunTransitionService() {}

    /**
     * What {@code event} does to {@code run}, without touching anything.
     *
     * <p>Note what is <b>not</b> here: a "this move was already applied" success. A state machine
     * cannot apply the same move twice, because applying it moved the state -- a replay therefore
     * finds no transition and is refused as {@link Reason#ILLEGAL_EVENT}. The committed-key ledger
     * exists for the economic commits of P9 (TDS #30) and, until then, as the detector below: two
     * different outcomes under one key is the failure the key is meant to prevent.
     */
    public static Outcome decide(PersistedRun run, RunEvent event, long now) {
        Optional<RunTransitions.Transition> found = RunTransitions.lookup(run.state(), event);
        if (found.isEmpty()) {
            return new Refusal(Reason.ILLEGAL_EVENT, event + " is not legal from " + run.state()
                    + "; legal here: " + RunTransitions.eventsFrom(run.state()));
        }
        RunTransitions.Transition transition = found.get();
        if (transition.resumeFromCheckpoint()) {
            return new Refusal(Reason.NOT_RESUMABLE_YET, "resuming a run needs an instance to resume into,"
                    + " which arrives with P3; the run stays parked in " + RunState.RECOVERY_REQUIRED);
        }

        // The floor BEFORE the move, which is what makes {nextFloor} name the floor being opened.
        String key = transition.checkpoint() ? transition.key(run.runId(), run.floorIndex()) : "";
        if (!key.isEmpty() && run.hasCommitted(key)) {
            return new Refusal(Reason.KEY_REUSED, "checkpoint key " + key + " has already been committed by this run;"
                    + " committing it again would mean two outcomes under one key");
        }

        int floor = event == RunEvent.NEXT_FLOOR_CONFIRMED ? run.floorIndex() + 1 : run.floorIndex();
        List<String> committed = new ArrayList<>(run.committedTransactions());
        if (!key.isEmpty()) committed.add(key);
        Optional<RunCheckpoint> checkpoint = transition.checkpoint()
                ? Optional.of(new RunCheckpoint(key, transition.next()))
                : run.lastCheckpoint();

        PersistedRun next = new PersistedRun(run.runId(), run.schemaVersion(), run.towerId(), run.towerRevision(),
                run.towerDigest(), run.rulesetRevision(), run.structureRevision(), run.seed(), floor,
                transition.next(), run.participants(), checkpoint, committed, now);
        return new Move(next, run.state(), transition.checkpoint(), key);
    }

    /**
     * Applies {@code event} to the run with this id, writing the result to the index and the store.
     *
     * <p>A checkpointing move is flushed to disk before this returns, so a crash immediately
     * afterwards finds the run where it was left rather than where it was minutes ago.
     */
    public static Outcome apply(MinecraftServer server, UUID runId, RunEvent event, long now) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) {
            return new Refusal(Reason.UNKNOWN_RUN, "no run with id " + runId);
        }
        Outcome outcome = decide(found.get(), event, now);
        if (outcome instanceof Move move) {
            TowerRuns.save(server, move.next(), move.checkpoint());
            TowerLog.info("Run {} {} -> {} on {}{}", runId, move.from(), move.next().state(), event,
                    move.checkpoint() ? " [checkpoint " + move.key() + "]" : "");
        }
        return outcome;
    }
}
