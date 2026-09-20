package com.cobbletowers.economy;

import net.minecraft.resources.ResourceLocation;

/**
 * The one reserved id a reward table can name to grant CobbleDollars instead of an item (TDS #18).
 *
 * <p>Never a registered Minecraft {@code Item}: it never becomes an {@code ItemStack}, so it needs no
 * model, no recipe, no registry entry. {@code RewardTableDefinition}, {@code RewardValuation} and the
 * ledger do not know this id is special -- they price and bank it exactly like any other entry.
 * {@code RewardDelivery} is the one place that recognizes it and credits {@link
 * com.cobbletowers.persistence.TowerWalletStore} instead of inserting a stack.
 */
public final class CobbleDollars {

    public static final ResourceLocation ITEM_ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "cobble_dollar");

    private CobbleDollars() {}
}
