package com.cobbletowers.runtime;

import com.cobbletowers.ServerState;
import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.participant.ConnectionState;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.battle.cobblemon.CobblemonBattleAdapter;
import com.cobbletowers.diagnostics.TowerMetrics;
import com.cobbletowers.encounter.TowerEncounters;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.modifier.DraftService;
import com.cobbletowers.persistence.PersistedRun;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who is actually here: disconnects, reconnects and stalled players (TDS #36, #37, #59). The stall check runs on a
 * slow timer.
 */
public final class TowerPresence {

    /**
     * How long one battle may produce nothing before its player is dropped. A cap on a stuck battle, not a turn
     * timer.
     */
    public static final long PLAYER_STALL_MILLIS = 10L * 60 * 1000;

    /** How long a whole floor may produce nothing before the run is parked as a technical fault (not a loss). */
    public static final long FLOOR_STALL_MILLIS = 30L * 60 * 1000;

    /** How often the watchdog looks. Nothing here is urgent; a stalled floor stays stalled. */
    static final long SWEEP_INTERVAL_MILLIS = 30L * 1000;

    /** When each disconnected player dropped. In memory only: a restart parks every interrupted run. */
    private static final Map<UUID, Long> DISCONNECTED_AT = new LinkedHashMap<>();

    private static long lastSweep;

    private TowerPresence() {}

    public static void install() {
        // Fabric fires this on the Netty thread that saw the socket close (several at once when a party drops
        // together), and everything onDisconnect touches is single-threaded state, so hop to the server thread.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            ServerThread.run(server, () -> guarded("handle a disconnect", () -> onDisconnect(server, player)));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                guarded("handle a join", () -> onJoin(server, handler.getPlayer())));
        // Per tick, this compares two longs and returns. The work itself happens on the interval
        // above; TDS section 11 forbids per-tick scans, not per-tick clock reads.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            if (now - lastSweep < SWEEP_INTERVAL_MILLIS) return;
            lastSweep = now;
            long started = System.nanoTime();
            guarded("sweep the tower floors", () -> sweep(server, now));
            TowerMetrics.recordTick(server, "presence sweep", (System.nanoTime() - started) / 1_000_000);
        });
    }

    /** A player dropped out. Only the connection axis changes; their battle ends. */
    static void onDisconnect(MinecraftServer server, ServerPlayer player) {
        if (player == null) return;
        UUID playerId = player.getUUID();
        // This now runs after the event fired, possibly a tick later. If the same person has already logged back in
        // on a
        // new connection, this is the OLD connection's disconnect, and marking them disconnected would be wrong.
        ServerPlayer current = server.getPlayerList().getPlayer(playerId);
        if (current != null && current != player) return;
        Optional<PersistedRun> run = ParticipantService.runOf(playerId);
        if (run.isEmpty() || !run.get().state().isLive()) return;

        long now = System.currentTimeMillis();
        DISCONNECTED_AT.put(playerId, now);
        ParticipantService.update(server, run.get().runId(), playerId, ParticipantState::disconnected, now);
        TowerEncounters.dropPlayer(server, run.get().runId(), playerId, "disconnected", now);
        TowerLog.info("Player {} left run {} mid-floor; their place is held for {} minute(s)",
                player.getGameProfile().getName(), run.get().runId(),
                ParticipantService.RECONNECT_WINDOW_MILLIS / 60_000);
    }

    /**
     * A player came back. Inside the window they return as a spectator until the next intermission (TDS #37); past it
     * they are out of the floor but not marked as having left (TDS #39).
     */
    static void onJoin(MinecraftServer server, ServerPlayer player) {
        if (player == null) return;
        UUID playerId = player.getUUID();
        Long since = DISCONNECTED_AT.remove(playerId);
        Optional<PersistedRun> found = ParticipantService.runOf(playerId);
        if (found.isEmpty() || !found.get().state().isLive()) return;
        PersistedRun run = found.get();

        long now = System.currentTimeMillis();
        boolean late = since != null && now - since > ParticipantService.RECONNECT_WINDOW_MILLIS;
        // Spectating only if a floor is actually in progress.
        boolean floorInProgress = TowerEncounters.of(run.runId()).isPresent();
        // A boss fight CobbleRaids is holding their slot in: they are still a participant, so resume it, not watch it.
        boolean resumes = TowerEncounters.of(run.runId())
                .filter(round -> round.phase() == TowerEncounters.Phase.BOSS
                        && round.byPlayer().get(playerId) != TowerEncounters.Status.OUT)
                .isPresent() && CobblemonBattleAdapter.inLiveBattle(player);
        boolean spectate = floorInProgress && !resumes;
        ParticipantService.update(server, run.runId(), playerId,
                state -> spectate ? state.reconnected().spectating() : state.reconnected(), now);
        if (resumes) {
            TowerLog.info("Player {} rejoined run {} and resumes the boss fight",
                    player.getGameProfile().getName(), run.runId());
            return;
        }
        if (!floorInProgress) {
            TowerLog.info("Player {} rejoined run {}{}, which is between floors",
                    player.getGameProfile().getName(), run.runId(), late ? " after their window closed" : "");
            return;
        }
        TowerRuns.get(run.runId()).ifPresent(current ->
                TowerEncounters.sendToSpectatorAnchor(server, current, player));
        TowerLog.info("Player {} rejoined run {}{} as a spectator until the next intermission",
                player.getGameProfile().getName(), run.runId(), late ? " after their window closed" : "");
    }

    /** The player chose to leave. Terminal for them, and the end of the run if they were the last. */
    public static boolean leave(MinecraftServer server, ServerPlayer player, long now) {
        Optional<PersistedRun> found = ParticipantService.runOf(player.getUUID());
        if (found.isEmpty() || !found.get().state().isLive()) return false;
        UUID runId = found.get().runId();

        DISCONNECTED_AT.remove(player.getUUID());
        TowerEncounters.dropPlayer(server, runId, player.getUUID(), "left the run", now);
        Optional<PersistedRun> after =
                ParticipantService.update(server, runId, player.getUUID(), ParticipantState::left, now);
        TowerLog.info("Player {} left run {}", player.getGameProfile().getName(), runId);

        if (after.isPresent() && !anybodyCouldReturn(after.get(), now)) {
            TowerLog.info("Run {} has nobody left in it; ending it", runId);
            RunTransitionService.apply(server, runId, RunEvent.ABANDON_REQUESTED, now);
        }
        return true;
    }

    /**
     * What the watchdog decided about one floor.
     * @param drop players who stopped answering
     * @param park the floor stalled, so the run is a technical fault
     */
    public record Verdict(List<UUID> drop, boolean park) {

        public Verdict {
            drop = List.copyOf(drop);
        }

        public boolean isQuiet() {
            return drop.isEmpty() && !park;
        }
    }

    /** The watchdog's judgement, pure: readings in, verdict out. A stalled floor outranks its stalled players. */
    public static Verdict judge(Map<UUID, Long> lastActivityByPlayer, long roundStartedAt, long now) {
        long newest = roundStartedAt;
        for (long last : lastActivityByPlayer.values()) newest = Math.max(newest, last);
        if (now - newest > FLOOR_STALL_MILLIS) return new Verdict(List.of(), true);

        List<UUID> stalled = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : lastActivityByPlayer.entrySet()) {
            if (now - entry.getValue() > PLAYER_STALL_MILLIS) stalled.add(entry.getKey());
        }
        return new Verdict(stalled, false);
    }

    /** When each run's draft was first seen with nobody able to vote. In memory, so a restart restarts the window. */
    private static final Map<UUID, Long> DRAFT_EMPTY_SINCE = new ConcurrentHashMap<>();

    static {
        ServerState.onStop(TowerPresence::onServerStopped);
    }

    /**
     * Expired grace windows, then stalled floors. Public so {@code runs watchdog} can run it with the clock wound
     * forward.
     */
    public static void sweep(MinecraftServer server, long now) {
        expireGraceWindows(server, now);
        settleAbandonedDrafts(server, now);

        for (TowerEncounters.Round round : TowerEncounters.activeRounds()) {
            // The boss is CobbleRaids', and it has its own lifetime cap on a boss that goes nowhere.
            // A second timer on one fight would only give the two something to disagree about.
            if (round.phase() != TowerEncounters.Phase.PREREQUISITE) continue;

            Verdict verdict = judge(CobblemonBattleAdapter.lastActivityByPlayer(round.runId()),
                    round.startedAt(), now);
            if (verdict.isQuiet()) continue;

            if (verdict.park()) {
                TowerLog.error("Floor {} of run {} has produced nothing for {} minute(s); parking the run",
                        round.floorIndex(), round.runId(), FLOOR_STALL_MILLIS / 60_000);
                TowerEncounters.abandon(server, round.runId());
                RunTransitionService.apply(server, round.runId(), RunEvent.TECHNICAL_FAILURE, now);
                continue;
            }
            for (UUID playerId : verdict.drop()) {
                TowerLog.warn("Player {} has not moved floor {} of run {} for {} minute(s); dropping them",
                        playerId, round.floorIndex(), round.runId(), PLAYER_STALL_MILLIS / 60_000);
                TowerEncounters.dropPlayer(server, round.runId(), playerId, "stopped answering", now);
            }
        }
    }

    /**
     * Settles drafts nobody is left to answer, from the seed. A party that has gone would otherwise hold its cell
     * forever.
     */
    private static void settleAbandonedDrafts(MinecraftServer server, long now) {
        for (PersistedRun run : TowerRuns.all()) {
            if (run.isRetired() || !run.modifiers().hasOpenDraft()) {
                DRAFT_EMPTY_SINCE.remove(run.runId());
                continue;
            }
            boolean anybodyHere = false;
            for (UUID voter : DraftService.voters(run)) {
                if (server.getPlayerList().getPlayer(voter) != null) {
                    anybodyHere = true;
                    break;
                }
            }
            if (anybodyHere) {
                DRAFT_EMPTY_SINCE.remove(run.runId());
                continue;
            }

            // Emptiness must last the grace window: a restart sweeps before anyone has reconnected. The stamp is real
            // time; {@code now} may be wound forward by {@code runs watchdog}.
            long since = DRAFT_EMPTY_SINCE.computeIfAbsent(run.runId(), ignored -> System.currentTimeMillis());
            if (now - since <= ParticipantService.RECONNECT_WINDOW_MILLIS) continue;

            DRAFT_EMPTY_SINCE.remove(run.runId());
            TowerLog.warn("Run {} has had a draft open at floor {} with nobody to vote for {} minute(s);"
                            + " settling it", run.runId(), run.floorIndex(),
                    ParticipantService.RECONNECT_WINDOW_MILLIS / 60_000);
            DraftService.settle(server, run.runId(), now);
        }
    }

    /**
     * Disconnected players whose window is up: KNOCKED_OUT rather than VOLUNTARILY_LEFT, so an intermission can take
     * them back. The run ends if nobody is online.
     */
    private static void expireGraceWindows(MinecraftServer server, long now) {
        for (Map.Entry<UUID, Long> entry : Map.copyOf(DISCONNECTED_AT).entrySet()) {
            if (now - entry.getValue() <= ParticipantService.RECONNECT_WINDOW_MILLIS) continue;
            UUID playerId = entry.getKey();
            DISCONNECTED_AT.remove(playerId);

            Optional<PersistedRun> found = ParticipantService.runOf(playerId);
            if (found.isEmpty() || !found.get().state().isLive()) continue;
            UUID runId = found.get().runId();

            ParticipantService.update(server, runId, playerId, ParticipantState::knockedOut, now);
            TowerLog.info("Player {} did not come back within the window; they are out of the floor of run {}",
                    playerId, runId);

            TowerRuns.get(runId).ifPresent(run -> {
                if (anybodyCouldReturn(run, now)) return;
                TowerLog.info("Run {} has nobody online and nobody expected back; ending it", runId);
                RunTransitionService.apply(server, runId, RunEvent.ABANDON_REQUESTED, now);
            });
        }
    }

    /** True while somebody is still playing this run, or still has time to come back to it. */
    private static boolean anybodyCouldReturn(PersistedRun run, long now) {
        for (PersistedParticipant participant : run.participants()) {
            if (!participant.state().isInRun()) continue;
            if (participant.state().connection() == ConnectionState.ONLINE) return true;
            Long since = DISCONNECTED_AT.get(participant.playerId());
            if (since != null && now - since <= ParticipantService.RECONNECT_WINDOW_MILLIS) return true;
        }
        return false;
    }

    /** Contained: exceptions here would unwind into the tick. */
    private static void guarded(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException ex) {
            TowerLog.error("CobbleTowers could not " + what, ex);
        }
    }

    /** Per-server state on a class that outlives a server: cleared, like every other index. */
    public static void onServerStopped() {
        DISCONNECTED_AT.clear();
        DRAFT_EMPTY_SINCE.clear();
        lastSweep = 0;
    }

    /** Who is inside a grace window right now, for the debug command and the live test. */
    public static Optional<Long> disconnectedAt(UUID playerId) {
        return Optional.ofNullable(DISCONNECTED_AT.get(playerId));
    }
}
