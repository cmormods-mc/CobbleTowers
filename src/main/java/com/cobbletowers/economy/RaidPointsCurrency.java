package com.cobbletowers.economy;

import net.minecraft.resources.ResourceLocation;

/**
 * The reserved id a reward table can name to grant CobbleRaids' Raid Points instead of an item (P21).
 *
 * <p>The same arrangement as {@link CobbleDollars}: never a registered {@code Item}, priced and banked like any
 * other entry, and recognised in exactly one place -- {@code RewardDelivery} -- which credits the player's balance
 * through CobbleRaids' public {@code CobbleRaidsPoints} API rather than inserting a stack.
 */
public final class RaidPointsCurrency {

    public static final ResourceLocation ITEM_ID = ResourceLocation.fromNamespaceAndPath("cobbleraids", "raid_points");

    private RaidPointsCurrency() {}
}
