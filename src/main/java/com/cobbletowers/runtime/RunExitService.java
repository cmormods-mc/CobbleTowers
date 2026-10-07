package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.participant.MembershipState;
import com.cobbletowers.instance.TowerDimension;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.ReturnPoint;
import com.cobbletowers.persistence.TowerReturnStore;
import com.cobbletowers.runtime.ExitRules.Standing;
import com.cobbletowers.runtime.ExitRules.Verdict;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Sends players home when they have no live place in a run (P20), and only then lets a finished run's cell
 * be reset. See {@code docs/design/P20-leaving-the-tower.md}.
 *
 * <p>One rule, enforced by a once-a-second sweep: nobody stays in the tower dimension without a live place
 * in a run. A run ending, a player leaving, a dropped connection, a crash and a late login are all just
 * ways of breaking that rule, so none of them needs its own code.
 */
public final class RunExitService {

    private static final int SWEEP_EVERY_TICKS = 20;
    private static int ticks;

    /**
     * Operators in creative or spectator who were in the tower when their own run ended: they go home like anyone else, instead of being
     * exempt as explorers. In memory on purpose; a restart loses the mark, and the cell release still sends every participant home.
     */
    private static final java.util.Set<UUID> LEAVING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private RunExitService() {}

    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % SWEEP_EVERY_TICKS != 0) return;
            try {
                sweep(server, System.currentTimeMillis());
            } catch (RuntimeException ex) {
                TowerLog.error("The tower exit sweep failed", ex);
            }
        });
    }

    /** An operator in creative or spectator mode, who may be in the tower to look around. */
    private static boolean operatorExploring(ServerPlayer player) {
        return player.hasPermissions(2) && (player.isCreative() || player.isSpectator());
    }

    private static boolean inTower(ServerPlayer player) {
        return player.level().dimension().equals(TowerDimension.LEVEL);
    }

    /**
     * Records where the player is standing, just before the first teleport into the tower. Skipped for a
     * player already inside it, so a second floor never overwrites the real starting place with a spot in
     * an arena. Flushed, so a crash while they are inside still leaves a way home.
     */
    public static void remember(MinecraftServer server, ServerPlayer player) {
        if (inTower(player)) return;
        TowerReturnStore store = TowerReturnStore.get(server);
        store.put(player.getUUID(), new ReturnPoint(player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        store.checkpoint(server);
    }

    /**
     * True when a finished run's cell cannot be reset yet because somebody is standing in it. The run keeps
     * its lease for the beat and the sweep releases it once they have gone.
     */
    public static boolean mustDefer(MinecraftServer server, PersistedRun run) {
        return run.cell().isPresent() && anyoneInside(server, run);
    }

    public static void announce(MinecraftServer server, PersistedRun run) {
        for (PersistedParticipant participant : run.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player != null && inTower(player)) {
                if (operatorExploring(player)) LEAVING.add(player.getUUID());
                player.sendSystemMessage(Component.literal("The run is over. You will be returned in "
                        + ExitRules.BEAT_MILLIS / 1000 + " seconds."));
            }
        }
    }

    private static void evacuateParticipants(MinecraftServer server, PersistedRun run) {
        for (PersistedParticipant participant : run.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player == null || !inTower(player)) continue;
            // Someone already in a new run of their own is not being stranded by this cell: leave them where they are.
            if (!ExitRules.mayEvacuateAtRelease(standingOf(participant.playerId()))) continue;
            try {
                evacuate(server, player);
            } catch (RuntimeException ex) {
                TowerLog.error("Could not send " + participant.playerId() + " out of a cell that is being released", ex);
            }
        }
    }

    private static boolean anyoneInside(MinecraftServer server, PersistedRun run) {
        for (PersistedParticipant participant : run.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player != null && inTower(player)) return true;
        }
        return false;
    }

    static void sweep(MinecraftServer server, long now) {
        // Watchers whose run has ended go home before anything is released under them.
        com.cobbletowers.spectator.Watching.sweep(server);
        // Players first, so a cell is never reset under someone the same pass was about to move.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (com.cobbletowers.spectator.Watching.isWatching(player.getUUID())) continue;
            if (!inTower(player)) {
                LEAVING.remove(player.getUUID());
                continue;
            }
            boolean exempt = ExitRules.exempt(operatorExploring(player), LEAVING.contains(player.getUUID()));
            if (ExitRules.decide(true, exempt, standingOf(player.getUUID()), now) == Verdict.LEAVE) {
                try {
                    evacuate(server, player);
                } catch (RuntimeException ex) {
                    // One player who cannot be moved must not strand everyone after them in the list.
                    TowerLog.error("Could not send " + player.getUUID() + " out of the tower", ex);
                }
            }
        }
        for (PersistedRun run : TowerRuns.all()) {
            if (!run.isRetired() || run.cell().isEmpty()) continue;
            if (ExitRules.releaseDue(anyoneInside(server, run), run.updatedAt(), now)) {
                // One cell per window (HeavyWork); the rest wait for the next sweep. Players are still sent home by the loop above.
                if (!com.cobbletowers.instance.HeavyWork.tryAcquire(now)) break;
                // A cell is never reset under the people who were in the run: anyone still inside (an operator the loop above
                // chose to leave alone, say) goes home first, or they would be left standing in an empty dimension.
                evacuateParticipants(server, run);
                RunTransitionService.releaseCell(server, run, now);
            }
        }
    }

    /**
     * {@code /tower leave} for a player stuck in the tower after their run ended: sends them home now instead of
     * waiting for the sweep. Refuses while they still have a live place in a run.
     *
     * @return the sentence to show, or null when this does not apply (not in the tower)
     */
    public static String leaveNow(MinecraftServer server, ServerPlayer player) {
        if (!inTower(player)) return null;
        if (standingOf(player.getUUID()).kind() == Standing.Kind.ACTIVE) {
            return "Your run is still going. Use the menu (/tower) to cash out, or finish the floor.";
        }
        evacuate(server, player);
        return "Sent home.";
    }

    /**
     * How a player stands with the runs they have been in.
     *
     * <p>Looks through every run rather than asking {@link TowerRuns#forPlayer}, because that index drops a
     * run the moment it finishes (so a finished run never stops a player starting another) -- which would make
     * a player whose run just ended look like they had never been in one, and skip the beat.
     */
    static Standing standingOf(UUID playerId) {
        PersistedRun lastEnded = null;
        boolean endedAndLeft = false;
        for (PersistedRun run : TowerRuns.all()) {
            for (PersistedParticipant participant : run.participants()) {
                if (!participant.playerId().equals(playerId)) continue;
                boolean left = participant.state().membership() == MembershipState.VOLUNTARILY_LEFT;
                if (!run.isRetired() && !left) return Standing.active();
                if (run.isRetired() && (lastEnded == null || run.updatedAt() > lastEnded.updatedAt())) {
                    lastEnded = run;
                    endedAndLeft = left;
                }
            }
        }
        if (lastEnded == null || endedAndLeft) return Standing.none();
        return Standing.ended(lastEnded.updatedAt());
    }

    /** Back to where they started, or the overworld's spawn if that is gone or was never recorded. */
    public static void sendHome(MinecraftServer server, ServerPlayer player) {
        evacuate(server, player);
    }

    static void evacuate(MinecraftServer server, ServerPlayer player) {
        TowerReturnStore store = TowerReturnStore.get(server);
        Optional<ReturnPoint> point = store.pointFor(player.getUUID());

        ServerLevel level = null;
        if (point.isPresent()) {
            ResourceLocation id = ResourceLocation.tryParse(point.get().dimension());
            if (id != null) level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
        if (level != null && level.dimension().equals(TowerDimension.LEVEL)) level = null;

        if (level == null) {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld == null) return;
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0f, 0.0f);
            TowerLog.info("Sent {} out of the tower to the overworld spawn ({})", player.getUUID(),
                    point.isPresent() ? "their starting place was unavailable" : "no starting place was recorded");
        } else {
            ReturnPoint at = point.get();
            player.teleportTo(level, at.x(), at.y(), at.z(), at.yaw(), at.pitch());
            TowerLog.info("Sent {} out of the tower back to {} at {}, {}, {}", player.getUUID(), at.dimension(),
                    Math.round(at.x()), Math.round(at.y()), Math.round(at.z()));
        }
        store.remove(player.getUUID());
        store.checkpoint(server);
        LEAVING.remove(player.getUUID());
        com.cobbletowers.spectator.Watching.restoreMode(player);
        player.sendSystemMessage(Component.literal("You have left the tower."));
    }
}
