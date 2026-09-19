package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.persistence.PersistedRun;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;

/**
 * What happens to runs that were in flight when the server stopped.
 *
 * <p>A run that was live has no instance any more -- the world came back without one -- so it is
 * parked in RECOVERY_REQUIRED, keeping the checkpoint it last committed. That is the deterministic
 * legal outcome TDS #28 asks for, and it is never reported as a player loss: the run was not lost,
 * it is waiting.
 *
 * <p>Parking goes through the ordinary transition service rather than assigning a state directly,
 * so recovery obeys the same table, the same key rules and the same write policy as every other
 * move. Runs already terminal or already parked are left alone.
 */
public final class RunRecovery {

    private RunRecovery() {}

    /** Parks every live run. Returns how many were parked. */
    public static int recoverInterruptedRuns(MinecraftServer server, long now) {
        int parked = 0;
        for (PersistedRun run : TowerRuns.all()) {
            if (!run.state().isLive()) continue;
            OptionalInt cell = run.cell();
            RunTransitionService.Outcome outcome =
                    RunTransitionService.apply(server, run.runId(), RunEvent.TECHNICAL_FAILURE, now);
            if (outcome instanceof RunTransitionService.Move) {
                parked++;
                // Whatever was fighting when the server went down is still standing in the cell.
                // Left there, it is found when the run finally releases the cell, and the cell is
                // quarantined for contents this run put there and nobody cleaned up.
                if (cell.isPresent()) RecoverySweep.schedule(server, run.runId(), cell.getAsInt());
            } else if (outcome instanceof RunTransitionService.Refusal refusal) {
                // Reported rather than retried: a run the machine will not park is one a person
                // needs to look at, and silently leaving it live would let P3 try to resume it.
                TowerLog.error("Could not park interrupted tower run {} ({}): {}",
                        run.runId(), refusal.reason(), refusal.detail());
            }
        }
        if (parked > 0) {
            TowerLog.warn("{} tower run(s) were interrupted by a restart and are awaiting recovery.", parked);
        }
        return parked;
    }
}
