package com.cobbletowers.modifier;

import com.cobbletowers.definition.ModifierDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Whether a set of modifiers can legally be held together (TDS #58). {@link #eligible} validates the whole resulting
 * set rather than checking the candidate alone, so a later rule cannot be silently missed. Pure.
 */
public final class ModifierResolver {

    private ModifierResolver() {}

    /** Everything wrong with holding exactly this set, empty if nothing. Order-independent. */
    public static List<String> validate(List<ModifierDefinition> set) {
        List<String> problems = new ArrayList<>();

        Map<ResourceLocation, Integer> counts = new HashMap<>();
        Map<String, ResourceLocation> groupOwner = new HashMap<>();
        for (ModifierDefinition modifier : set) {
            counts.merge(modifier.id(), 1, Integer::sum);
        }

        for (Map.Entry<ResourceLocation, Integer> entry : counts.entrySet()) {
            ModifierDefinition modifier = first(set, entry.getKey());
            if (modifier != null && entry.getValue() > modifier.stackLimit()) {
                problems.add(entry.getKey() + " is held " + entry.getValue() + " times, over its stack limit of "
                        + modifier.stackLimit());
            }
        }

        for (ModifierDefinition modifier : set) {
            // Exclusion is read both ways. Content declares it on one side and expects it to hold;
            // requiring both files to list each other would make a half-declared pair legal.
            for (ResourceLocation excluded : modifier.excludes()) {
                if (counts.containsKey(excluded)) {
                    problems.add(modifier.id() + " cannot be held with " + excluded);
                }
            }
            for (ResourceLocation required : modifier.requires()) {
                if (!counts.containsKey(required)) {
                    problems.add(modifier.id() + " requires " + required + ", which is not held");
                }
            }
            // A group admits one modifier, not one copy; the stack limit governs repeats of the same id.
            modifier.group().ifPresent(group -> {
                ResourceLocation owner = groupOwner.putIfAbsent(group, modifier.id());
                if (owner != null && !owner.equals(modifier.id())) {
                    problems.add(modifier.id() + " and " + owner + " are both in group '" + group + "'");
                }
            });
        }
        return List.copyOf(problems);
    }

    /** Whether the set that would result from taking {@code candidate} is legal. */
    public static boolean eligible(ModifierDefinition candidate, List<ModifierDefinition> held) {
        List<ModifierDefinition> resulting = new ArrayList<>(held.size() + 1);
        resulting.addAll(held);
        resulting.add(candidate);
        return validate(resulting).isEmpty();
    }

    /** Everything in {@code pool} that could be taken next, in the pool's own order. */
    public static List<ModifierDefinition> eligibleFrom(List<ModifierDefinition> pool,
                                                        List<ModifierDefinition> held) {
        List<ModifierDefinition> offers = new ArrayList<>();
        for (ModifierDefinition candidate : pool) {
            if (candidate.effectiveNow() && eligible(candidate, held)) offers.add(candidate);
        }
        return List.copyOf(offers);
    }

    private static ModifierDefinition first(List<ModifierDefinition> set, ResourceLocation id) {
        for (ModifierDefinition modifier : set) {
            if (modifier.id().equals(id)) return modifier;
        }
        return null;
    }
}
