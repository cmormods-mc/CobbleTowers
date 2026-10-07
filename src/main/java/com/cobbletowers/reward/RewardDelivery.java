package com.cobbletowers.reward;

import com.cobbletowers.TowerLog;
import com.cobbleraids.api.points.CobbleRaidsPoints;
import com.cobbletowers.economy.CobbleDollars;
import com.cobbletowers.economy.RaidPointsCurrency;
import com.cobbletowers.network.RewardRevealPayload;
import com.cobbletowers.network.TowerNetworking;
import com.cobbletowers.persistence.PendingTowerReward;
import com.cobbletowers.persistence.TowerPendingRewardStore;
import com.cobbletowers.persistence.TowerWalletStore;
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
 * Hands a player whatever the tower owes them as soon as they can receive it. No choice and no claim command (P9): a
 * roll from {@link RewardValuation} is simply delivered.
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
        boolean creditedWallet = false;
        for (PendingTowerReward reward : queue) {
            if (reward.item().equals(CobbleDollars.ITEM_ID)) {
                // A player's mastery of the tower the reward was earned in (P31) adds to their CobbleDollars.
                int bonus = com.cobbletowers.mastery.MasteryService.perksForRun(server, player.getUUID(), reward.runId())
                        .cobbleDollarBonusPercent();
                TowerWalletStore.get(server).credit(player.getUUID(), reward.amount() + (long) reward.amount() * bonus / 100);
                delivered.add(reward);
                creditedWallet = true;
            } else if (reward.item().equals(RaidPointsCurrency.ITEM_ID)) {
                // CobbleRaids' own currency, credited through its public API (P21).
                CobbleRaidsPoints.award(server, player.getUUID(),
                        com.cobbletowers.armor.ArmorBonusEffects.raidPoints(player, reward.amount(), reward.runId()));
                delivered.add(reward);
            } else if (!reward.components().isEmpty()) {
                // A card (P33b): an item that is its data. Not handed over, and not lost, if the mod that owns it is
                // gone.
                if (giveWithComponents(player, reward)) delivered.add(reward);
            } else {
                // The 777 Unique (AscensionLib) can enlarge an item reward; what is listed to the player is what was
                // given. The key is the
                // reward itself, so the rounding of a fraction is the same however often delivery is retried.
                int amount = com.cobbletowers.economy.AscensionLibItemBonus.scale(player.getUUID(), reward.amount(),
                        reward.runId() + "|" + reward.floorIndex() + "|" + reward.item() + "|" + reward.grantedAt());
                if (give(player, reward.item(), amount)) {
                    delivered.add(amount == reward.amount() ? reward : new PendingTowerReward(reward.runId(), reward.floorIndex(),
                            reward.item(), amount, reward.grantedAt(), reward.components(), reward.label()));
                }
            }
        }
        store.checkpoint(server);
        if (creditedWallet) TowerWalletStore.get(server).checkpoint(server);
        if (!delivered.isEmpty()) {
            player.sendSystemMessage(Component.literal("Tower rewards delivered: " + summarize(delivered)));
            TowerNetworking.sendRewardReveal(player, revealOf(delivered));
        }
        return delivered.size();
    }

    /** The screen payload, sent alongside the chat line; a client without the channel still gets text. */
    private static RewardRevealPayload revealOf(List<PendingTowerReward> delivered) {
        int throughFloor = delivered.stream().mapToInt(PendingTowerReward::floorIndex).max().orElse(0);
        List<RewardRevealPayload.Grant> grants = new ArrayList<>(delivered.size());
        for (PendingTowerReward reward : delivered) {
            grants.add(new RewardRevealPayload.Grant(reward.item(), reward.amount(), reward.label()));
        }
        return new RewardRevealPayload(throughFloor, List.copyOf(grants));
    }

    /**
     * Places one reward in the inventory, dropping the rest at the player's feet. Returns false, granting nothing,
     * when the item no longer resolves ({@code BuiltInRegistries.ITEM.get} returns air for an unknown id).
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

    /**
     * Builds the stack the pending reward describes and places it in the inventory; false, granting nothing, if it
     * does not resolve.
     */
    private static boolean giveWithComponents(ServerPlayer player, PendingTowerReward reward) {
        java.util.Optional<ItemStack> built = com.cobbletowers.rental.CardStacks.parse(player.registryAccess(), reward.components());
        if (built.isEmpty()) {
            TowerLog.error("A pending tower reward ({}) could not be turned into an item; skipping it. Is the mod that owns {} installed?",
                    reward.label().isEmpty() ? reward.item() : reward.label(), reward.item());
            return false;
        }
        ItemStack stack = built.get();
        stack.setCount(reward.amount());
        player.getInventory().placeItemBackInInventory(stack);
        return true;
    }

    private static String summarize(List<PendingTowerReward> rewards) {
        StringBuilder builder = new StringBuilder();
        for (PendingTowerReward reward : rewards) {
            if (builder.length() > 0) builder.append(", ");
            if (!reward.label().isEmpty()) {
                builder.append(reward.label());
                if (reward.amount() > 1) builder.append(" x").append(reward.amount());
            } else {
                builder.append(reward.item()).append(" x").append(reward.amount());
            }
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
