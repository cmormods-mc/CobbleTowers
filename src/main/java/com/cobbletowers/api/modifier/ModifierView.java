package com.cobbletowers.api.modifier;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One modifier, as an addon may read it.
 *
 * <p>Read-only and free of any live object, like every other view in this package. What a modifier
 * <i>does</i> is deliberately not here: an addon can see that a run is carrying
 * {@code cobbletowers:crowded_floor} and what it excludes, without CobbleTowers having to make its
 * internal effect payload a public contract it can never change.
 */
public interface ModifierView {

    ResourceLocation id();

    ModifierType type();

    /** Untranslated; P11 owns presentation. */
    String displayName();

    RiskTier risk();

    /**
     * The mutual-exclusion group this belongs to, if any.
     *
     * <p>At most one modifier from a group can be held at a time -- the coarse rule that saves
     * declaring every pair in {@link #excludes()} (TDS #58).
     */
    Optional<String> group();

    /** Modifiers that cannot be held alongside this one. Symmetric: the resolver reads it both ways. */
    List<ResourceLocation> excludes();

    /** Modifiers that must already be held before this one can be offered. */
    List<ResourceLocation> requires();

    /** How many copies a run may accumulate. At least 1. */
    int stackLimit();
}
