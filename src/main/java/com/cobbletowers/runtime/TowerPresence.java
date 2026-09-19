package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.participant.ConnectionState;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.battle.cobblemon.CobblemonBattleAdapter;
import com.cobbletowers.encounter.TowerEncounters;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who is actually here, and what to do about the ones who are not (TDS #36, #37, #59).
 *
 * <p>Three things arrive here that a floor cannot notice by itself: a player losing connection, one
 * coming back, and one who is still connected but has stopped playing. The first two are events. The
 * third is the only thing in this mod that has to be looked for, and it is looked for on a slow
 * timer rather than per tick.
 */
public final class TowerPresence {

    /**
     * How long one prerequisite battle may go without producing anything before its player is
     * dropped from the floor.
     *
     * <p>Ten minutes, which is far beyond how long a single opponent takes -- the live floors have
     * run in seconds. It is a cap on a stuck battle, not a turn timer: Cobblemon raises no per-turn
     * event, so what can honestly be observed from out here is the battle starting and things
     * fainting in it, and a battle that has produced neither in ten minutes is not being played.
     */
    public static final long PLAYER_STALL_MILLIS = 10L * 60 * 1000;

    /**
     * How long a whole floor may produce nothing before the run is parked as a technical fault.
     *
     * <p>Deliberately much longer than one player's cap, and a different verdict: one player who has
     * stopped is a player problem and the floor carries on without them, but a floor where nobody is
     * getting anywhere is the engine, and nothing on it can be trusted to be scored. Parking is not a
     * loss -- the run resumes from its checkpoint.
     */
    public static final long FLOOR_STALL_MILLIS = 30L * 60 * 1000;

    /** How often the watchdog looks. Nothing here is urgent; a stalled floor stays stalled. */
    static final long SWEEP_INTERVAL_MILLIS = 30L * 1000;

    /**
     * When each disconnected player dropped, held in memory only.
     *
     * <p>Not persisted, and it does not need to be: a restart parks every interrupted run, so there
     * is no floor left running for a grace window to expire into. Writing a countdown to disk that
     * could only ever be read back after it had become meaningless is worse than not writing it.
     */
    private static final Map<UUID, Long> DISCONNECTED_AT = new LinkedHashMap<>();

    private static long lastSweep;

    private TowerPresence() {}

    public static void install() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                guarded("handle a disconnect", () -> onDisconnect(server, handler.getPlayer())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                guarded("handle a join", () -> onJoin(server, handler.getPlayer())));
        // Per tick, this compares two longs and returns. The work itself happens on the interval
        // above; TDS section 11 forbids per-tick scans, not per-tick clock reads.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            if (now - lastSweep < SWEEP_INTERVAL_MILLIS) return;
            lastSweep = now;
            guarded("sweep the tower floors", () -> sweep(server, now));
        });
    }

    /**
     * A player dropped out.
     *
     * <p>The connection axis only, which is the whole reason P1 separated the axes: whatever they
     * were doing in combat is still recorded, so coming back is a restoration rather than a guess.
     * Their battle ends all the same -- Cobblemon will not run one for a player who is not there,
     * and their opponent left standing in the cell is what the sweep quarantines it for.
     */
    static void onDisconnect(MinecraftServer server, ServerPlayer player) {
        if (player == null) return;
        UUID playerId = player.getUUID();
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
     * A player came back.
     *
     * <p>Inside the window they return as a spectator until the next intermission (TDS #37): the
     * floor they left has moved on without them, and slotting a player into a battle already being
     * fought is not something Cobblemon offers. The intermission is where everybody who is out comes
     * back at once. Past the window they are out of the floor -- and deliberately not marked as
     * having left, which is a choice only the player makes (TDS #39).
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
        // Spectating only if there is something to spectate. A run between floors has moved on
        // without them in no way at all, and marking them a spectator there would make them sit out
        // an intermission they are standing in.
        boolean floorInProgress = TowerEncounters.of(run.runId()).isPresent();
        ParticipantService.update(server, run.runId(), playerId,
                state -> floorInProgress ? state.reconnected().spectating() : state.reconnected(), now);
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
     * What the watchdog decided about one floor, worked out from nothing but clock readings.
     *
     * @param drop players who have stopped answering
     * @param park the whole floor has stalled, so the run is a technical fault
     */
    public record Verdict(List<UUID> drop, boolean park) {

        public Verdict {
            drop = List.copyOf(drop);
        }

        public boolean isQuiet() {
            return drop.isEmpty() && !park;
        }
    }

    /**
     * The watchdog's judgement, pure: activity readings in, a verdict out.
     *
     * <p>Separated from the sweep so both cases can be tested from a clock rather than from a sleep,
     * and so the rule is written once somewhere it can be read.
     *
     * <p>A stalled floor outranks its stalled players. If nothing at all has happened for half an
     * hour, the answer is not "drop everybody", which would score the floor as a wipe; it is that
     * the tower cannot be trusted, and the run parks where it can be resumed.
     */
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

    /**
     * Expired grace windows, then stalled floors.
     *
     * <p>Public because {@code /cobbletowers runs watchdog} runs exactly this with the clock wound
     * forward. A test-only threshold would have proved a code path that only tests take.
     */
    public static void sweep(MinecraftServer server, long now) {
        expireGraceWindows(server, now);

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
     * Disconnected players whose five minutes are up.
     *
     * <p>They are out of the floor, not out of the run: {@code KNOCKED_OUT} rather than
     * {@code VOLUNTARILY_LEFT}, so an intermission can still take them back if they return. When the
     * last window expires with nobody online, the run ends rather than holding its cell.
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

    /**
     * Contained: these run from connection handling and from the server tick, where an exception
     * does not stay local -- it unwinds into the tick and takes the server with it.
     */
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
        lastSweep = 0;
    }

    /** Who is inside a grace window right now, for the debug command and the live test. */
    public static Optional<Long> disconnectedAt(UUID playerId) {
        return Optional.ofNullable(DISCONNECTED_AT.get(playerId));
    }
}
