package com.cobbletowers.report;

import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.mastery.TuningCounters;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.TowerRuns;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the beta wants to know about runs (P48): a history line per transition for reports, and tallies in the tuning
 * counters (run starts and ends, floors entered, where runs were lost). Never throws: a tally must not disturb a run.
 */
public final class RunTelemetry {

    private RunTelemetry() {}

    /** Called by the transition service for every move a run makes. */
    public static void onTransition(MinecraftServer server, UUID runId, RunState from, RunState to, RunEvent event) {
        try {
            RunHistory.note(runId, java.time.LocalTime.now().withNano(0) + " " + from + " -> " + to + " on " + event);
            Optional<PersistedRun> found = TowerRuns.get(runId);
            if (found.isEmpty()) return;
            PersistedRun run = found.get();
            if (to == RunState.ALLOCATING_INSTANCE && from == RunState.VALIDATING_PARTY) {
                TuningCounters.bump(server, "run_start.total");
                TuningCounters.bump(server, "run_start." + startKind(run));
            }
            if (to == RunState.ENCOUNTER_ACTIVE && from == RunState.FLOOR_READY) {
                TuningCounters.bump(server, "floor_entered." + floorKey(run.floorIndex()));
            }
            if (to.isTerminal()) {
                TuningCounters.bump(server, "run_end." + to.name().toLowerCase(Locale.ROOT));
                if (to == RunState.FAILED || to == RunState.ABANDONED) {
                    TuningCounters.bump(server, "run_lost_at." + floorKey(run.floorIndex()));
                }
                prompt(server, run);
            }
        } catch (RuntimeException ex) {
            // Telemetry only.
        }
    }

    /** {@code trial_daily}, {@code trial_weekly}, {@code rental}, {@code playlist} or {@code plain}. */
    static String startKind(PersistedRun run) {
        Optional<String> trial = run.options().trial();
        if (trial.isPresent()) return trial.get().startsWith("weekly") ? "trial_weekly" : "trial_daily";
        if (run.options().playlist().isPresent()) {
            return run.options().playlist().get().getPath().equals("rental") ? "rental" : "playlist";
        }
        return "plain";
    }

    /** {@code f05}: floors sort in order in the counter report. */
    static String floorKey(int floor) {
        return String.format(Locale.ROOT, "f%02d", Math.max(0, Math.min(99, floor)));
    }

    private static void prompt(MinecraftServer server, PersistedRun run) {
        if (!BetaConfig.reportPrompt()) return;
        List<PersistedParticipant> participants = run.participants();
        for (PersistedParticipant participant : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player != null) {
                player.sendSystemMessage(Component.literal("Beta: found a problem or have an idea? /tower report <what happened>")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
