package com.cobbletowers.rental;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

/**
 * Turns a {@link RentalCards.Spec} into a real item (P33b) through the game's item parser, using plain names so no
 * CobblemonCards class is needed. If the mod is absent the item does not resolve and the answer is empty, never an
 * exception or air.
 */
public final class CardStacks {

    private CardStacks() {}

    /** The card as an item, or empty if the card mod is not installed or its data does not parse. */
    public static Optional<ItemStack> of(HolderLookup.Provider registries, RentalCards.Spec card) {
        return parse(registries, card.toItemTag());
    }

    /** The item that {@link RentalCards.Spec#toItemTag()}, written as text, describes, or empty. */
    public static Optional<ItemStack> parse(HolderLookup.Provider registries, String itemTagText) {
        try {
            return parse(registries, TagParser.parseTag(itemTagText));
        } catch (CommandSyntaxException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static Optional<ItemStack> parse(HolderLookup.Provider registries, CompoundTag tag) {
        try {
            return ItemStack.parse(registries, tag).filter(stack -> !stack.isEmpty());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}
