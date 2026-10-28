package com.cobbletowers.reward;

import com.cobbletowers.TowerLog;
import com.cobbletowers.economy.TowerKeys;
import com.cobbletowers.persistence.PendingTowerReward;
import com.cobbletowers.persistence.RunOptions;
import com.cobbletowers.persistence.TowerPendingRewardStore;
import java.util.Collection;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a daily or weekly trial pays after its one scored attempt: nothing from the tower's loot, only a {@value #KEY_PERCENT}
 * percent chance of one rental key per player. The roll is seeded by (encounter, player) and queued with
 * {@code addIfAbsent}, so a replay after a crash names the same result and never pays twice.
 */
public final class PracticeTrialReward {

    public static final int KEY_PERCENT = 10;

    private PracticeTrialReward() {}

    /** A trial run that is not the player's scored attempt. */
    public static boolean isPractice(RunOptions options) {
        return options.isTrial() && !options.scored();
    }

    /** Whether this player's practice run turns up a rental key. Pure and repeatable. */
    public static boolean rollsKey(UUID encounterId, UUID player) {
        var random = new Random(encounterId.getMostSignificantBits() * 31 + encounterId.getLeastSignificantBits() * 17
                + player.getMostSignificantBits() * 13 + player.getLeastSignificantBits());
        return random.nextInt(100) < KEY_PERCENT;
    }

    /** Queues and hands over the keys the practice run rolled. */
    public static void grantKeys(MinecraftServer server, UUID encounterId, Collection<UUID> players, long now) {
        TowerPendingRewardStore queue = TowerPendingRewardStore.get(server);
        var won = new java.util.ArrayList<UUID>();
        for (UUID player : players) {
            if (!rollsKey(encounterId, player)) continue;
            queue.addIfAbsent(player, new PendingTowerReward(encounterId, 1, TowerKeys.Kind.RENTAL.id(), 1, now));
            won.add(player);
        }
        TowerLog.info("Practice trial {}: {} of {} player(s) rolled a rental key", encounterId, won.size(), players.size());
        if (won.isEmpty()) return;
        queue.checkpoint(server);
        for (UUID id : won) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) RewardDelivery.deliver(server, player);
        }
    }
}
