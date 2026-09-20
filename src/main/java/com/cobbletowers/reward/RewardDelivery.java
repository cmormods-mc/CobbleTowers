package com.cobbletowers.reward;

import com.cobbletowers.TowerLog;
import com.cobbletowers.network.RewardRevealPayload;
import com.cobbletowers.network.TowerNetworking;
import com.cobbletowers.persistence.PendingTowerReward;
import com.cobbletowers.persistence.TowerPendingRewardStore;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Hands a player whatever the tower owes them, the moment they are somewhere to receive it.
 *
 * <p>No choice, no claim command -- P9's deliberate simplification over CobbleRaids'
 * {@code RaidRewardService}, whose GUI and per-player claim locking exist only because that mod lets
 * a player pick between several reward choices. Nothing here ever offers a choice, so a roll from
 * {@link RewardValuation} is simply delivered.
 */
public final class RewardDelivery {

    private RewardDelivery() {}

    /** Subscribed once, for the life of the JVM, the way every other event install here is. */
    public static void install() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                guarded(() -> deliver(server, handler.getPlayer())));
    }

    /** Drains {@code player}'s queue into their inventory, overflow dropped at their feet. */
    public static int deliver(MinecraftServer server, ServerPlayer player) {
        TowerPendingRewardStore store = TowerPendingRewardStore.get(server);
        List<PendingTowerReward> queue = store.drain(player.getUUID());
        if (queue.isEmpty()) return 0;

        List<PendingTowerReward> delivered = new ArrayList<>(queue.size());
        for (PendingTowerReward reward : queue) {
            if (give(player, reward.item(), reward.amount())) delivered.add(reward);
        }
        store.checkpoint(server);
        if (!delivered.isEmpty()) {
            player.sendSystemMessage(Component.literal("Tower rewards delivered: " + summarize(delivered)));
            TowerNetworking.sendRewardReveal(player, revealOf(delivered));
        }
        return delivered.size();
    }

    /**
     * The screen's payload, alongside the chat line above rather than instead of it (P11): a client
     * without the channel registered still gets the grant as text, exactly as it does today.
     */
    private static RewardRevealPayload revealOf(List<PendingTowerReward> delivered) {
        int throughFloor = delivered.stream().mapToInt(PendingTowerReward::floorIndex).max().orElse(0);
        List<RewardRevealPayload.Grant> grants = new ArrayList<>(delivered.size());
        for (PendingTowerReward reward : delivered) {
            grants.add(new RewardRevealPayload.Grant(reward.item(), reward.amount()));
        }
        return new RewardRevealPayload(throughFloor, List.copyOf(grants));
    }

    /**
     * Places one reward in the inventory, dropping the remainder at the player's feet when it does
     * not fit. Returns false, having granted nothing, when the item no longer resolves.
     *
     * <p>The same shape CobbleRaids' own {@code RaidRewardGrantEngine.give} uses, including the
     * resolution check: {@code BuiltInRegistries.ITEM.get} returns air for an unknown id rather than
     * throwing, so a definition an unrelated mod update broke is skipped and logged rather than
     * silently handed out as air.
     */
    private static boolean give(ServerPlayer player, ResourceLocation itemId, int amount) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (!BuiltInRegistries.ITEM.getKey(item).equals(itemId)) {
            TowerLog.error("A pending tower reward names item {}, which is not registered; skipping it.", itemId);
            return false;
        }
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = new ItemStack(item);
            int chunk = Math.min(remaining, stack.getMaxStackSize());
            stack.setCount(chunk);
            player.getInventory().placeItemBackInInventory(stack);
            remaining -= chunk;
        }
        return true;
    }

    private static String summarize(List<PendingTowerReward> rewards) {
        StringBuilder builder = new StringBuilder();
        for (PendingTowerReward reward : rewards) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(reward.item()).append(" x").append(reward.amount());
        }
        return builder.toString();
    }

    private static void guarded(Runnable work) {
        try {
            work.run();
        } catch (RuntimeException ex) {
            TowerLog.error("CobbleTowers could not deliver a pending tower reward", ex);
        }
    }
}
