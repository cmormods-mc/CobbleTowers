package com.cobbletowers.api.regional;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * What a regional theme is, without exposing how weighting or aspects are resolved.
 *
 * <p>Jersey species ids only, not their aspects or how much a floor weights toward them: an addon
 * asking "is this a jersey Pokemon" is all TDS #79's exclusivity promise asks anyone outside this mod
 * to know.
 */
public interface RegionalThemeView {

    ResourceLocation id();

    String displayName();

    /** Display-only (TDS #80); nothing in this mod reads it to change behavior. */
    String doctrine();

    /** The five jersey signature species of this theme, in declared order. */
    List<ResourceLocation> jerseySpeciesIds();
}
