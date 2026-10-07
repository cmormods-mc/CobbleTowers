package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.definition.FloorLayout;
import com.cobbletowers.definition.RulesetDefinition;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.encounter.TowerEncounters;
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
 * The steps that are more than one transition: taking an instance, building it, and giving up when there is none.
 * {@link RunTransitionService} knows only the table.
 */
public final class RunLifecycle {

    private RunLifecycle() {}

    /**
     * Submits a run's parties and settles them (TDS #41, #46): read, register, check against the ruleset, then
     * allocate an instance or abandon with reasons logged. Registration is written before the verdict event. {@code
     * partyOf} is empty for an offline player, which rejects the run.
     */
    public static RunTransitionService.Outcome validateParty(MinecraftServer server, UUID runId, long now,
                                                             Function<UUID, Optional<List<PartyMember>>> partyOf) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) {
            return new RunTransitionService.Refusal(RunTransitionService.Reason.UNKNOWN_RUN, "no run with id " + runId);
        }
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(found.get().towerId());
        RulesetDefinition ruleset = com.cobbletowers.definition.RulesetResolver.forRun(TowerDefinitionRegistry.content(),
                found.get(), Optional.empty());
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
            // A playlist's clauses (P32) apply to the Pokemon that would be registered.
            run.options().playlist().flatMap(com.cobbletowers.definition.PlaylistRegistry::get)
                    .ifPresent(playlist -> PlaylistRules.problems(party.get().stream()
                                    .filter(member -> result.registered().contains(member.id())).toList(), playlist)
                            .forEach(problem -> problems.add(participant.playerId() + " " + problem)));
            registered.add(new PersistedParticipant(participant.playerId(), participant.state(), result.registered()));
        }
        TowerRuns.save(server, run.withParticipants(registered, now), false);

        if (problems.isEmpty()) {
            return RunTransitionService.apply(server, runId, RunEvent.PARTY_VALIDATED, now);
        }
        TowerLog.info("Run {} party rejected: {}", runId, problems);
        return RunTransitionService.apply(server, runId, RunEvent.PARTY_REJECTED, now);
    }

    /** How opening a floor went. */
    public enum Opened {
        /** The floor is up and the team is in it. */
        OPENED,
        /** The table refused the move (an open draft, say); nothing changed and the run is not parked. */
        BLOCKED,
        /** Something broke and the run was parked for recovery: broken content is not a loss. */
        FAILED
    }

    /**
     * ENCOUNTER_STARTED and the floor's first round for a run at FLOOR_READY; parks the run with TECHNICAL_FAILURE if
     * either fails.
     */
    public static boolean beginFloor(MinecraftServer server, UUID runId, long now) {
        if (!(RunTransitionService.apply(server, runId, RunEvent.ENCOUNTER_STARTED, now)
                instanceof RunTransitionService.Move)) {
            RunTransitionService.apply(server, runId, RunEvent.TECHNICAL_FAILURE, now);
            return false;
        }
        try {
            if (TowerEncounters.begin(server, runId).isPresent()) return true;
        } catch (RuntimeException ex) {
            TowerLog.error("Opening floor of run {} threw", runId, ex);
        }
        RunTransitionService.apply(server, runId, RunEvent.TECHNICAL_FAILURE, now);
        return false;
    }

    /**
     * Leaves an intermission for the next floor, rebuilding the cell in place if the next floor is a different
     * structure, then begins the floor.
     */
    public static Opened openNextFloor(MinecraftServer server, UUID runId, long now) {
        if (!(RunTransitionService.apply(server, runId, RunEvent.INTERMISSION_COMPLETE, now)
                instanceof RunTransitionService.Move)) {
            return Opened.BLOCKED;
        }
        if (!(RunTransitionService.apply(server, runId, RunEvent.NEXT_FLOOR_CONFIRMED, now)
                instanceof RunTransitionService.Move)) {
            RunTransitionService.apply(server, runId, RunEvent.TECHNICAL_FAILURE, now);
            return Opened.FAILED;
        }
        if (!rebuildIfStructureChanged(server, runId)) {
            RunTransitionService.apply(server, runId, RunEvent.TECHNICAL_FAILURE, now);
            return Opened.FAILED;
        }
        return beginFloor(server, runId, now) ? Opened.OPENED : Opened.FAILED;
    }

    private static boolean rebuildIfStructureChanged(MinecraftServer server, UUID runId) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty() || found.get().cell().isEmpty()) return false;
        PersistedRun run = found.get();
        Optional<FloorLayout> next = TowerDefinitionRegistry.content().floorAt(run.towerId(), run.floorIndex())
                .flatMap(floor -> floor.layout());
        Optional<FloorLayout> previous = TowerDefinitionRegistry.content().floorAt(run.towerId(), run.floorIndex() - 1)
                .flatMap(floor -> floor.layout());
        if (next.isEmpty()) return false;
        if (previous.isPresent() && previous.get().structure().equals(next.get().structure())) return true;

        int cell = run.cell().getAsInt();
        // prepare() clears the previous floor itself (the cell is dirty), so there is no separate reset: one scan,
        // not two.
        Optional<CellPreparer.Prepared> prepared = CellPreparer.prepare(server, cell, next.get());
        // This cannot wait for a slot (the party is between floors), so it is noted: everything else backs off around
        // it.
        com.cobbletowers.instance.HeavyWork.note(System.currentTimeMillis());
        if (prepared.isEmpty() || !prepared.get().isPlayable()) {
            TowerLog.error("Cell {} could not be rebuilt as {} for run {}: {}", cell, next.get().structure(), runId,
                    prepared.map(CellPreparer.Prepared::summary).orElse("the structure did not place"));
            return false;
        }
        return true;
    }

    /**
     * Gives a run a cell, builds its floor and moves it on, or parks it. Order matters: warm pool first; lease
     * written onto the run before the INSTANCE_ALLOCATED checkpoint; any failure becomes ALLOCATION_FAILED (a
     * technical fault, not a loss).
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
        // The pool is told what to keep ready and builds it from its own tick, not here: a cell built in this tick
        // would stall the run start again.
        CellWarmPool.want(layout.get());
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
            // A floor that builds wrong is broken content, not a bad cell, so the cell goes back rather than into
            // quarantine.
            TowerLog.error("Cell {} could not be made playable for {}: {}", cell, layout.structure(),
                    prepared.map(CellPreparer.Prepared::summary).orElse("the structure did not place"));
            InstanceAllocator.release(server, runId, cell);
            return OptionalInt.empty();
        }
        return OptionalInt.of(cell);
    }
}
