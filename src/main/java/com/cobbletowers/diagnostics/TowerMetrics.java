package com.cobbletowers.diagnostics;

import com.cobbletowers.TowerLog;
import com.cobbletowers.persistence.TowerDiagnosticsStore;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * The one place every other package reports a timing (TDS #33, #60, section 11).
 *
 * <p>Five categories, matching section 11's own list exactly: allocation, encounter construction,
 * transition, cleanup and tick cost. Persistence backlog is a count, not a timing -- there is nothing
 * to time about a queue depth.
 *
 * <p>Every method here is a thin call into {@link TowerDiagnosticsStore} plus one budget comparison;
 * nothing computes, blocks or does I/O. Called from the server thread everywhere it is called, the
 * same way the timings it records already are.
 */
public final class TowerMetrics {

    public static final String ALLOCATION = "allocation";
    public static final String ENCOUNTER_CONSTRUCTION = "encounter_construction";
    public static final String TRANSITION = "transition";
    public static final String CLEANUP = "cleanup";
    public static final String TICK = "tick";

    private TowerMetrics() {}

    public static void recordAllocation(MinecraftServer server, long millis) {
        if (sample(server, ALLOCATION, millis, DiagnosticBudgets.ALLOCATION_BUDGET_MILLIS)) {
            warn(ALLOCATION, millis, DiagnosticBudgets.ALLOCATION_BUDGET_MILLIS, null);
        }
    }

    public static void recordEncounterConstruction(MinecraftServer server, UUID runId, long millis) {
        TowerDiagnosticsStore.get(server).recordEncounterConstruction(runId, millis, System.currentTimeMillis());
        if (sample(server, ENCOUNTER_CONSTRUCTION, millis, DiagnosticBudgets.ENCOUNTER_CONSTRUCTION_BUDGET_MILLIS)) {
            warn(ENCOUNTER_CONSTRUCTION, millis, DiagnosticBudgets.ENCOUNTER_CONSTRUCTION_BUDGET_MILLIS, runId);
        }
    }

    public static void recordTransition(MinecraftServer server, UUID runId, long millis) {
        TowerDiagnosticsStore.get(server).recordTransition(runId, millis, System.currentTimeMillis());
        if (sample(server, TRANSITION, millis, DiagnosticBudgets.TRANSITION_BUDGET_MILLIS)) {
            warn(TRANSITION, millis, DiagnosticBudgets.TRANSITION_BUDGET_MILLIS, runId);
        }
    }

    public static void recordCleanup(MinecraftServer server, long millis) {
        if (sample(server, CLEANUP, millis, DiagnosticBudgets.CLEANUP_BUDGET_MILLIS)) {
            warn(CLEANUP, millis, DiagnosticBudgets.CLEANUP_BUDGET_MILLIS, null);
        }
    }

    /** Only the tick that did real work calls this -- the cheap clock-compare on every other tick is not the cost. */
    public static void recordTick(MinecraftServer server, String source, long millis) {
        if (sample(server, TICK, millis, DiagnosticBudgets.TICK_BUDGET_MILLIS)) {
            TowerLog.warn("{} tick work took {}ms, over the {}ms budget", source, millis,
                    DiagnosticBudgets.TICK_BUDGET_MILLIS);
        }
    }

    /** TDS's "persistence backlog": how many writes have landed since the last checkpoint cleared it. */
    public static void recordNonCheckpointedWrite(MinecraftServer server) {
        TowerDiagnosticsStore.get(server).recordNonCheckpointedWrite();
    }

    public static void recordCheckpoint(MinecraftServer server) {
        TowerDiagnosticsStore.get(server).recordCheckpoint();
    }

    /** @return whether this sample crossed its budget */
    private static boolean sample(MinecraftServer server, String category, long millis, long budget) {
        boolean over = millis > budget;
        TowerDiagnosticsStore.get(server).sample(category, millis, over);
        return over;
    }

    private static void warn(String category, long millis, long budget, UUID runId) {
        if (runId != null) {
            TowerLog.warn("{} took {}ms for run {}, over the {}ms budget", category, millis, runId, budget);
        } else {
            TowerLog.warn("{} took {}ms, over the {}ms budget", category, millis, budget);
        }
    }
}
