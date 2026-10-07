package com.cobbletowers.api.regional;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** What a regional theme is: jersey species ids only, without how weighting or aspects are resolved (TDS #79). */
public interface RegionalThemeView {

    ResourceLocation id();

    String displayName();

    /** Display-only (TDS #80); nothing in this mod reads it to change behavior. */
    String doctrine();

    /** The five jersey signature species of this theme, in declared order. */
    List<ResourceLocation> jerseySpeciesIds();
}
