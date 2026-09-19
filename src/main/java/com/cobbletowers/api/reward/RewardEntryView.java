package com.cobbletowers.api.reward;

import net.minecraft.resources.ResourceLocation;

/** One possible item a reward table can roll, weighted against the rest of its tier. */
public interface RewardEntryView {

    ResourceLocation item();

    int minAmount();

    int maxAmount();

    int weight();
}
