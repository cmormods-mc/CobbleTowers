package com.cobbletowers.echo;

import com.cobbletowers.TowerLog;
import com.cobbletowers.battle.cobblemon.CobblemonBattleAdapter;
import com.cobbletowers.persistence.TowerEchoStore;
import com.cobbletowers.persistence.TowerWalletStore;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Echo Duels in progress (P35): which players of which run are in an exhibition battle against which Echo.
 *
 * <p>In memory only. A duel is a bonus: a restart ends the battle with the server, and losing the record of it costs nothing,
 * so nothing here is persisted. While any player of a run is still duelling the run may not ready up, cash out or move on;
 * a player who disconnects or whose battle has ended is dropped on the next look, so a stuck duel cannot hold a run shut.
 */
public final class EchoDuels {

    private static final Map<UUID, Map<UUID, UUID>> PENDING = new HashMap<>();

    private EchoDuels() {}

    /** Records that {@code player} is duelling {@code echo}. */
    static void begin(UUID runId, UUID player, UUID echo) {
        PENDING.computeIfAbsent(runId, id -> new LinkedHashMap<>()).put(player, echo);
    }

    /**
     * Whether anyone in the run is still in a duel. Drops those who are offline or no longer in a battle first, so this is
     * also the self-healing that keeps a vanished battle from blocking the intermission.
     */
    public static boolean active(MinecraftServer server, UUID runId) {
        Map<UUID, UUID> pending = PENDING.get(runId);
        if (pending == null) return false;
        pending.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null || !CobblemonBattleAdapter.inBattle(player);
        });
        if (pending.isEmpty()) PENDING.remove(runId);
        return PENDING.containsKey(runId);
    }

    /** A duel battle ended (called by the encounter for an exhibition binding). */
    public static void onResolved(MinecraftServer server, CobblemonBattleAdapter.Binding binding, boolean playerWon) {
        Map<UUID, UUID> pending = PENDING.get(binding.runId());
        UUID echoId = pending == null ? null : pending.remove(binding.playerId());
        if (pending != null && pending.isEmpty()) PENDING.remove(binding.runId());
        ServerPlayer player = server.getPlayerList().getPlayer(binding.playerId());
        TowerEchoStore echoes = TowerEchoStore.get(server);
        if (echoId != null) echoes.recordResult(echoId, !playerWon);
        if (playerWon) {
            TowerWalletStore.get(server).credit(binding.playerId(), EchoPolicy.DUEL_REWARD);
            if (player != null) {
                player.sendSystemMessage(Component.literal("You won the Echo Duel: +" + EchoPolicy.DUEL_REWARD
                        + " CobbleDollars. Your party was not touched."));
            }
        } else if (player != null) {
            player.sendSystemMessage(Component.literal("The Echo won this time. Nothing was lost: your party was not touched."));
        }
        TowerLog.info("Echo duel of run {} ended: {} {}", binding.runId(), binding.playerId(), playerWon ? "won" : "lost");
    }

    /** Ends every duel of a run (it left the intermission, was abandoned or cashed out). */
    public static void cancel(MinecraftServer server, UUID runId) {
        Map<UUID, UUID> pending = PENDING.remove(runId);
        if (pending == null) return;
        for (UUID id : pending.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) CobblemonBattleAdapter.endBattleOf(player);
        }
        TowerLog.info("Cancelled the Echo duel(s) of run {}", runId);
    }

    /** Forgets everything (server stop, tests). */
    public static void clear() {
        PENDING.clear();
    }
}
