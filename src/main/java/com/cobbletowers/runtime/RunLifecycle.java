package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.definition.FloorLayout;
import com.cobbletowers.definition.RulesetDefinition;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.instance.CellPreparer;
import com.cobbletowers.instance.CellWarmPool;
import com.cobbletowers.instance.InstanceAllocator;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.PartyValidation.PartyMember;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;

/**
 * The steps that are more than one transition: taking an instance, building it, and giving up when
 * there is none to be had.
 *
 * <p>Kept apart from {@link RunTransitionService}, which knows only about the table. This is where a
 * move needs something from the world before the state can change.
 */
public final class RunLifecycle {

    private RunLifecycle() {}

    /**
     * Submits a run's parties and settles them (TDS #41, #46): every participant's party is read,
     * registered and checked against the ruleset, then the run moves on to instance allocation, or is
     * abandoned with the reasons logged.
     *
     * <p>The registration is written <b>before</b> the verdict event, so the checkpoint that reaches
     * disk already names what was registered. {@code partyOf} is how the party is read -- empty for a
     * player who is not online, which rejects the run, since an absent party cannot be checked.
     */
    public static RunTransitionService.Outcome validateParty(MinecraftServer server, UUID runId, long now,
                                                             Function<UUID, Optional<List<PartyMember>>> partyOf) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) {
            return new RunTransitionService.Refusal(RunTransitionService.Reason.UNKNOWN_RUN, "no run with id " + runId);
        }
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(found.get().towerId());
        RulesetDefinition ruleset = tower == null ? null : TowerDefinitionRegistry.content().rulesets().get(tower.rulesetId());
        if (ruleset == null) {
            return new RunTransitionService.Refusal(RunTransitionService.Reason.ILLEGAL_EVENT,
                    "run " + runId + " has no ruleset to validate against");
        }

        RunTransitionService.Outcome submitted = RunTransitionService.apply(server, runId, RunEvent.PARTY_SUBMITTED, now);
        if (submitted instanceof RunTransitionService.Refusal) return submitted;

        // Re-read after the submit move: apply() wrote a fresh record, and saving participants onto
        // the stale one would undo the state change.
        PersistedRun run = TowerRuns.get(runId).orElseThrow();
        List<PersistedParticipant> registered = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (PersistedParticipant participant : run.participants()) {
            Optional<List<PartyMember>> party = partyOf.apply(participant.playerId());
            if (party.isEmpty()) {
                problems.add(participant.playerId() + " is not online to register a party");
                registered.add(participant);
                continue;
            }
            PartyValidation.Result result = PartyValidation.validate(party.get(), ruleset);
            result.problems().forEach(problem -> problems.add(participant.playerId() + " " + problem));
            registered.add(new PersistedParticipant(participant.playerId(), participant.state(), result.registered()));
        }
        TowerRuns.save(server, run.withParticipants(registered, now), false);

        if (problems.isEmpty()) {
            return RunTransitionService.apply(server, runId, RunEvent.PARTY_VALIDATED, now);
        }
        TowerLog.info("Run {} party rejected: {}", runId, problems);
        return RunTransitionService.apply(server, runId, RunEvent.PARTY_REJECTED, now);
    }

    /**
     * Finds the run a cell, builds its floor, and moves it on -- or parks it when it cannot.
     *
     * <p>Three orderings are load-bearing:
     * <ul>
     *   <li>the warm pool is asked first, because a cell that is already built is the difference
     *       between a party waiting and not;</li>
     *   <li>the lease is written onto the run <b>before</b> the INSTANCE_ALLOCATED checkpoint, so the
     *       checkpoint that reaches disk already names the cell -- otherwise a crash in between
     *       leaves a run past allocation holding nothing, and no later event fixes it;</li>
     *   <li>anything that goes wrong becomes ALLOCATION_FAILED, which the table maps to
     *       RECOVERY_REQUIRED: no cells, no structure, a broken floor -- all technical faults, and
     *       none of them a player's loss.</li>
     * </ul>
     */
    public static RunTransitionService.Outcome allocateInstance(MinecraftServer server, UUID runId, long now) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) {
            return new RunTransitionService.Refusal(RunTransitionService.Reason.UNKNOWN_RUN, "no run with id " + runId);
        }
        PersistedRun run = found.get();

        Optional<FloorLayout> layout = TowerDefinitionRegistry.content()
                .floorAt(run.towerId(), run.floorIndex())
                .flatMap(floor -> floor.layout());
        if (layout.isEmpty()) {
            TowerLog.error("Run {} is on {} floor {}, which declares no layout; parking it",
                    runId, run.towerId(), run.floorIndex());
            return RunTransitionService.apply(server, runId, RunEvent.ALLOCATION_FAILED, now);
        }

        OptionalInt cell = obtainCell(server, runId, layout.get());
        if (cell.isEmpty()) {
            return RunTransitionService.apply(server, runId, RunEvent.ALLOCATION_FAILED, now);
        }

        TowerRuns.save(server, run.withCell(cell, now), false);
        RunTransitionService.Outcome outcome =
                RunTransitionService.apply(server, runId, RunEvent.INSTANCE_ALLOCATED, now);
        if (outcome instanceof RunTransitionService.Refusal refusal) {
            // The move was refused after the cell was built, so give it straight back rather than
            // leaving a lease attached to a run that never advanced.
            InstanceAllocator.release(server, runId, cell.getAsInt());
            TowerRuns.save(server, run.withCell(OptionalInt.empty(), now), false);
            TowerLog.error("Run {} took cell {} and then could not advance ({}); the cell was returned",
                    runId, cell.getAsInt(), refusal.reason());
            return outcome;
        }

        // Only now, once a run has actually taken one, is there evidence the pool should hold more.
        CellWarmPool.topUp(server, layout.get(), now);
        return outcome;
    }

    /** A cell with this floor already built in it: from the pool if one is waiting, else built now. */
    private static OptionalInt obtainCell(MinecraftServer server, UUID runId, FloorLayout layout) {
        OptionalInt warm = CellWarmPool.take(layout.structure(), runId);
        if (warm.isPresent()) return warm;

        InstanceAllocator.Allocation allocation = InstanceAllocator.allocate(server, runId);
        if (allocation instanceof InstanceAllocator.Denied denied) {
            TowerLog.warn("Run {} could not be given an instance ({})", runId, denied.refusal());
            return OptionalInt.empty();
        }

        int cell = ((InstanceAllocator.Leased) allocation).cell();
        Optional<CellPreparer.Prepared> prepared = CellPreparer.prepare(server, cell, layout);
        if (prepared.isEmpty() || !prepared.get().isPlayable()) {
            // A floor that builds wrong is broken content, not a bad cell, so the cell goes back
            // rather than into quarantine -- the next run would fail the same way wherever it was
            // put, and quarantining would slowly eat the tower over a typo.
            TowerLog.error("Cell {} could not be made playable for {}: {}", cell, layout.structure(),
                    prepared.map(CellPreparer.Prepared::summary).orElse("the structure did not place"));
            InstanceAllocator.release(server, runId, cell);
            return OptionalInt.empty();
        }
        return OptionalInt.of(cell);
    }
}
