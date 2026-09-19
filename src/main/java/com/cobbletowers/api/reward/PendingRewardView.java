package com.cobbletowers.api.reward;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** One grant a player has not yet been handed, because they were not there to receive it. */
public interface PendingRewardView {

    ResourceLocation item();

    int amount();

    UUID runId();

    int floorIndex();
}
