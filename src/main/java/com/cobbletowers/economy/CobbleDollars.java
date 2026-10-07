package com.cobbletowers.economy;

import com.cobbletowers.CobbleTowers;
import net.minecraft.resources.ResourceLocation;

/**
 * The reserved id a reward table names to grant CobbleDollars (TDS #18). Never a registered {@code Item}; priced and
 * banked like any entry, and recognised only in {@code RewardDelivery}, which credits {@link
 * com.cobbletowers.persistence.TowerWalletStore}.
 */
public final class CobbleDollars {

    public static final ResourceLocation ITEM_ID = CobbleTowers.id("cobble_dollar");

    private CobbleDollars() {}
}
