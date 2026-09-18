package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.instance.InstanceAllocator;
import com.cobbletowers.persistence.PersistedRun;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * The steps that are more than one transition: taking an instance, and giving up when there is none.
 *
 * <p>Kept apart from {@link RunTransitionService}, which knows only about the table. This is where a
 * move needs something from the world before the state can change.
 */
public final class RunLifecycle {

    private RunLifecycle() {}

    /**
     * Leases a cell and moves the run on, or parks it when no cell can be had.
     *
     * <p>Order matters twice over. The lease is written onto the run <b>before</b> the
     * INSTANCE_ALLOCATED checkpoint, so the checkpoint that reaches disk already names the cell --
     * otherwise a crash in between would leave a run that is past allocation and holds nothing, which
     * no later event would fix. And a refusal is reported as ALLOCATION_FAILED, which the table maps
     * to RECOVERY_REQUIRED: running out of cells is a technical fault, never a player's loss.
     */
    public static RunTransitionService.Outcome allocateInstance(MinecraftServer server, UUID runId, long now) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) {
            return new RunTransitionService.Refusal(RunTransitionService.Reason.UNKNOWN_RUN, "no run with id " + runId);
        }
        PersistedRun run = found.get();

        InstanceAllocator.Allocation allocation = InstanceAllocator.allocate(server, runId);
        if (allocation instanceof InstanceAllocator.Denied denied) {
            TowerLog.warn("Run {} could not be given an instance ({}); parking it for recovery",
                    runId, denied.refusal());
            return RunTransitionService.apply(server, runId, RunEvent.ALLOCATION_FAILED, now);
        }

        int cell = ((InstanceAllocator.Leased) allocation).cell();
        TowerRuns.save(server, run.withCell(OptionalInt.of(cell), now), false);
        RunTransitionService.Outcome outcome =
                RunTransitionService.apply(server, runId, RunEvent.INSTANCE_ALLOCATED, now);
        if (outcome instanceof RunTransitionService.Refusal refusal) {
            // The move was refused after the cell was taken, so give it straight back rather than
            // leaving a lease attached to a run that never advanced.
            InstanceAllocator.release(server, runId, cell);
            TowerRuns.save(server, run.withCell(OptionalInt.empty(), now), false);
            TowerLog.error("Run {} took cell {} and then could not advance ({}); the cell was returned",
                    runId, cell, refusal.reason());
        }
        return outcome;
    }
}
