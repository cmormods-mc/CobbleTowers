package com.cobbletowers.economy;

import net.minecraft.resources.ResourceLocation;

/**
 * The reserved id a reward table names to grant CobbleRaids' Raid Points (P21). Like {@link CobbleDollars}:
 * recognised only in {@code RewardDelivery}, which credits through CobbleRaids' {@code CobbleRaidsPoints} API.
 */
public final class RaidPointsCurrency {

    public static final ResourceLocation ITEM_ID = ResourceLocation.fromNamespaceAndPath("cobbleraids", "raid_points");

    private RaidPointsCurrency() {}
}
