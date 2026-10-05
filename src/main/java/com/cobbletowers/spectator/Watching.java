package com.cobbletowers.spectator;

import com.cobbletowers.TowerLog;
import com.cobbletowers.instance.TowerDimension;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.RunExitService;
import com.cobbletowers.runtime.TowerRuns;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * {@code /tower watch <player>} (P36e): look in on someone else's live run from outside it.
 *
 * <p>The watcher is taken into the tower in spectator mode and rides the participant's camera, with the same panel a teammate sees. Nothing about a
 * watch is run state: it is held in memory only, and a restart simply ends it -- the exit sweep finds the watcher standing in the tower with no run and
 * sends them home. While it lasts the sweep leaves the watcher alone; once the run is over (or the person they follow goes) {@link #sweep} sends them
 * home before the cell is released, so nobody is ever left standing in an empty cell.
 */
public final class Watching {

    private record Watch(UUID runId, UUID targetId, GameType previousMode) {}

    private static final Map<UUID, Watch> WATCHES = new ConcurrentHashMap<>();

    private Watching() {}

    public static boolean isWatching(UUID player) {
        return WATCHES.containsKey(player);
    }

    /** How many people are watching a run, for the operator's diagnostics. */
    public static int watchersOf(UUID runId) {
        return (int) WATCHES.values().stream().filter(watch -> watch.runId().equals(runId)).count();
    }

    /** Begins watching, or returns why not. */
    public static String start(MinecraftServer server, ServerPlayer watcher, ServerPlayer target) {
        Optional<PersistedRun> run = TowerRuns.forPlayer(target.getUUID());
        boolean targetInLiveRun = run.isPresent() && !run.get().isRetired();
        Optional<String> refusal = WatchRules.refusal(new WatchRules.Situation(true, watcher.getUUID().equals(target.getUUID()),
                targetInLiveRun, target.level().dimension().equals(TowerDimension.LEVEL),
                TowerRuns.forPlayer(watcher.getUUID()).isPresent(), WATCHES.containsKey(watcher.getUUID())));
        if (refusal.isPresent()) return refusal.get();

        // Remember where they came from before the teleport, and keep the first mode if they switch to someone else.
        RunExitService.remember(server, watcher);
        Watch previous = WATCHES.get(watcher.getUUID());
        GameType mode = previous != null ? previous.previousMode() : watcher.gameMode.getGameModeForPlayer();
        WATCHES.put(watcher.getUUID(), new Watch(run.get().runId(), target.getUUID(), mode));

        watcher.setGameMode(GameType.SPECTATOR);
        watcher.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        watcher.setCamera(target);
        com.cobbletowers.network.TowerNetworking.sendSpectatorPanel(watcher, SpectatorPresentation.panelFor(run.get(), target));
        TowerLog.info("{} is watching {} in run {}", watcher.getUUID(), target.getUUID(), run.get().runId());
        return "Watching " + target.getGameProfile().getName() + ". Use /tower unwatch to go back.";
    }

    /** Ends a watch by the watcher's own choice. */
    public static String stop(MinecraftServer server, ServerPlayer watcher) {
        if (!WATCHES.containsKey(watcher.getUUID())) return "You are not watching anyone.";
        end(server, watcher.getUUID(), null);
        return "";
    }

    /** Ends every watch whose run is over or whose subject has gone; called by the exit sweep before it releases anything. */
    public static void sweep(MinecraftServer server) {
        for (UUID watcherId : new ArrayList<>(WATCHES.keySet())) {
            Watch watch = WATCHES.get(watcherId);
            if (watch == null) continue;
            ServerPlayer watcher = server.getPlayerList().getPlayer(watcherId);
            if (watcher == null) {
                WATCHES.remove(watcherId);
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(watch.targetId());
            boolean live = TowerRuns.get(watch.runId()).map(run -> !run.isRetired()).orElse(false);
            boolean targetIn = target != null && target.level().dimension().equals(TowerDimension.LEVEL);
            if (WatchRules.mustEnd(live, target != null, targetIn)) {
                end(server, watcherId, "The run you were watching has ended.");
            } else if (watcher.getCamera() != target && target != null) {
                watcher.setCamera(target);
            }
        }
    }

    /** Puts a player who is leaving the tower back in the game mode they had; a spectator with no record of one becomes a survival player. */
    public static void restoreMode(ServerPlayer player) {
        Watch watch = WATCHES.remove(player.getUUID());
        if (watch != null) {
            player.setCamera(player);
            player.setGameMode(watch.previousMode());
        } else if (player.isSpectator() && !player.hasPermissions(2)) {
            // A watcher whose server restarted mid-watch: the spectator mode outlived the watch.
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    private static void end(MinecraftServer server, UUID watcherId, String reason) {
        ServerPlayer watcher = server.getPlayerList().getPlayer(watcherId);
        if (watcher == null) {
            WATCHES.remove(watcherId);
            return;
        }
        if (reason != null) watcher.sendSystemMessage(Component.literal(reason));
        // sendHome restores the mode too, and drops the watch.
        RunExitService.sendHome(server, watcher);
        WATCHES.remove(watcherId);
    }
}
