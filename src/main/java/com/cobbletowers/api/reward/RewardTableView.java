package com.cobbletowers.api.reward;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** What a tower pays out, by what earned it. */
public interface RewardTableView {

    ResourceLocation id();

    String displayName();

    /** The pool a grant of this kind draws from. Empty when this table rolls nothing for it. */
    List<RewardEntryView> tier(RewardKind kind);

    /** How much a grant's amount grows per floor, as a percentage. */
    int growthPercentPerFloor();
}
