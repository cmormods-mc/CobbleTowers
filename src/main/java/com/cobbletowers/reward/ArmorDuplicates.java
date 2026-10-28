package com.cobbletowers.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Keeps a tower from paying a piece of armor a player already owns while a piece of the same set is still missing. A duplicate is swapped for one of
 * the missing pieces; once the player owns all four, a duplicate stands (it is the only thing left to give). Pure: the caller says what is owned.
 */
public final class ArmorDuplicates {

    private static final List<String> SLOTS = List.of("_helmet", "_chestplate", "_leggings", "_boots");

    private ArmorDuplicates() {}

    /** The slot suffix of a tower armor piece, or empty text when {@code item} is not one. */
    static String slotOf(ResourceLocation item) {
        if (!item.getNamespace().equals("cobbletowers")) return "";
        for (String slot : SLOTS) if (item.getPath().endsWith(slot)) return slot;
        return "";
    }

    /** All four pieces of the set {@code item} belongs to. Empty when it is not tower armor. */
    public static List<ResourceLocation> setOf(ResourceLocation item) {
        String slot = slotOf(item);
        if (slot.isEmpty()) return List.of();
        String set = item.getPath().substring(0, item.getPath().length() - slot.length());
        List<ResourceLocation> pieces = new ArrayList<>();
        for (String each : SLOTS) pieces.add(ResourceLocation.fromNamespaceAndPath(item.getNamespace(), set + each));
        return pieces;
    }

    /**
     * What to hand over instead of {@code item}.
     * @param owned the tower armor the player already has (worn, carried, in their ender chest or waiting to be claimed)
     * @param seed makes the pick repeatable, so a delivery retried after a crash names the same piece
     */
    public static ResourceLocation choose(ResourceLocation item, Set<ResourceLocation> owned, long seed) {
        if (!owned.contains(item)) return item;
        List<ResourceLocation> missing = new ArrayList<>();
        for (ResourceLocation piece : setOf(item)) if (!owned.contains(piece)) missing.add(piece);
        if (missing.isEmpty()) return item;
        return missing.get(new Random(seed).nextInt(missing.size()));
    }
}
