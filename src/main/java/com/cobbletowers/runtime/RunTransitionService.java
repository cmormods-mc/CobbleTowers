package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.encounter.TowerEncounters;
import com.cobbletowers.instance.InstanceAllocator;
import com.cobbletowers.persistence.CellStateStore;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.RunCheckpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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
        /** Nothing was ever committed, so there is no state to put the run back into. */
        NO_CHECKPOINT,
        /** The run's cell is gone or quarantined, so resuming would put it somewhere unusable. */
        CELL_UNAVAILABLE,
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
            return resume(run, now);
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
        // Only a KEYED move moves the checkpoint. A keyless checkpointing move -- parking a broken
        // run, abandoning it, resuming it -- forces a write but must leave the checkpoint where it
        // was: recording "committed at RECOVERY_REQUIRED" would make a later resume return the run
        // to the state it was trying to escape.
        Optional<RunCheckpoint> checkpoint = key.isEmpty()
                ? run.lastCheckpoint()
                : Optional.of(new RunCheckpoint(key, transition.next()));

        PersistedRun next = new PersistedRun(run.runId(), run.schemaVersion(), run.towerId(), run.towerRevision(),
                run.towerDigest(), run.rulesetRevision(), run.structureRevision(), run.seed(), floor,
                transition.next(), run.participants(), checkpoint, committed, now, run.cell(), run.ledger());
        return new Move(next, run.state(), transition.checkpoint(), key);
    }

    /**
     * Puts a parked run back where its checkpoint says it was.
     *
     * <p>The table cannot name the state, because it depends on the run rather than on the move --
     * which is what {@code resumeFromCheckpoint} means. A run with nothing committed has no state to
     * return to and is refused: that only happens when it was parked before its instance was
     * allocated, and such a run is abandoned rather than resumed.
     */
    private static Outcome resume(PersistedRun run, long now) {
        Optional<RunCheckpoint> checkpoint = run.lastCheckpoint();
        if (checkpoint.isEmpty()) {
            return new Refusal(Reason.NO_CHECKPOINT, "run " + run.runId() + " was parked before it committed"
                    + " anything, so there is no state to resume into; abandon it instead");
        }
        RunState target = checkpoint.get().state();
        PersistedRun next = new PersistedRun(run.runId(), run.schemaVersion(), run.towerId(), run.towerRevision(),
                run.towerDigest(), run.rulesetRevision(), run.structureRevision(), run.seed(), run.floorIndex(),
                target, run.participants(), run.lastCheckpoint(), run.committedTransactions(), now, run.cell(),
                run.ledger());
        // Durable, because a resume that a crash undoes leaves a run reported as recovered and
        // parked on disk -- the two states nobody can tell apart afterwards.
        return new Move(next, run.state(), true, "");
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
        PersistedRun run = found.get();
        if (event == RunEvent.RECOVERY_COMPLETED) {
            // Checked here rather than in decide, because whether a cell is usable is a question
            // about the world and decide is deliberately pure.
            Optional<Refusal> blocked = cellUnusable(server, run);
            if (blocked.isPresent()) return blocked.get();
        }
        Outcome outcome = decide(run, event, now);
        if (outcome instanceof Move move) {
            TowerRuns.save(server, move.next(), move.checkpoint());
            TowerLog.info("Run {} {} -> {} on {}{}", runId, move.from(), move.next().state(), event,
                    move.checkpoint() ? " [checkpoint " + move.key() + "]" : "");
            if (move.next().state().isTerminal()) releaseInstance(server, move.next(), now);
        }
        return outcome;
    }

    /**
     * Gives a finished run's cell back, and drops the lease from the run.
     *
     * <p>Tied to reaching a terminal state rather than to any particular event, so every way a run
     * can end -- completed, cashed out, wiped, abandoned -- returns its cell by the same path. A cell
     * that does not verify is quarantined by the allocator; either way the run stops holding it,
     * because a finished run holding a lease is a cell nothing will ever release.
     */
    private static void releaseInstance(MinecraftServer server, PersistedRun run, long now) {
        // Whatever the run was fighting stops first. An opponent left standing would be found by the
        // cell's cleanup sweep a moment later and quarantine the cell -- a poor way to discover that
        // a battle was not tidied up.
        TowerEncounters.abandon(server, run.runId());

        OptionalInt cell = run.cell();
        if (cell.isEmpty()) return;
        InstanceAllocator.Release release = InstanceAllocator.release(server, run.runId(), cell.getAsInt());
        if (release instanceof InstanceAllocator.Quarantined quarantined) {
            TowerLog.warn("Run {} ended leaving cell {} unfit for reuse: {}",
                    run.runId(), quarantined.cell(), quarantined.reason());
        }
        // Checkpointed: a dropped lease that a crash undoes would leave the cell claimed by a run
        // that has already finished, and nothing later would ever come back to release it.
        TowerRuns.save(server, run.withCell(OptionalInt.empty(), now), true);
    }

    /**
     * Whether the run's instance is fit to go back to.
     *
     * <p>A run that reached a checkpoint was allocated a cell at the same moment, so a missing lease
     * here means the save was edited or the cell was taken away. Either way, resuming into a cell
     * that is gone or quarantined would put players somewhere nobody has verified, which is the
     * thing quarantine exists to prevent.
     */
    private static Optional<Refusal> cellUnusable(MinecraftServer server, PersistedRun run) {
        OptionalInt cell = run.cell();
        if (cell.isEmpty()) {
            return Optional.of(new Refusal(Reason.CELL_UNAVAILABLE,
                    "run " + run.runId() + " holds no instance cell to resume into"));
        }
        if (CellStateStore.get(server).isQuarantined(cell.getAsInt())) {
            return Optional.of(new Refusal(Reason.CELL_UNAVAILABLE,
                    "cell " + cell.getAsInt() + " is quarantined; it must be cleared before the run can resume"));
        }
        return Optional.empty();
    }
}
