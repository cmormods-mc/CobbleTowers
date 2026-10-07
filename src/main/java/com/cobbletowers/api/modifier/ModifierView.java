package com.cobbletowers.api.modifier;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One modifier as an addon may read it: read-only, no live objects. What it does is deliberately not exposed, so the
 * effect payload is not a public contract.
 */
public interface ModifierView {

    ResourceLocation id();

    ModifierType type();

    /** Untranslated; P11 owns presentation. */
    String displayName();

    RiskTier risk();

    /** The mutual-exclusion group, if any; at most one modifier per group can be held (TDS #58). */
    Optional<String> group();

    /** Modifiers that cannot be held alongside this one. Symmetric: the resolver reads it both ways. */
    List<ResourceLocation> excludes();

    /** Modifiers that must already be held before this one can be offered. */
    List<ResourceLocation> requires();

    /** How many copies a run may accumulate. At least 1. */
    int stackLimit();
}
