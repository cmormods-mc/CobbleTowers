package com.cobbletowers.armor;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * An armor set as a tooltip needs to know it (P25): its name and colour, the item in each slot, and what each tier of
 * pieces does, already worded. This is what the server syncs to clients; it carries no behaviour, so a client can show a
 * set without being able to apply one.
 *
 * @param pieces the set's items, in head-to-feet order
 * @param tiers each distinct piece count that unlocks something, ascending, with the lines it unlocks
 */
public record ArmorSetView(ResourceLocation id, String name, int color, List<Piece> pieces, List<Tier> tiers) {

    /** One piece: the slot name ({@code head}, {@code chest}, {@code legs}, {@code feet}) and its item. */
    public record Piece(String slot, ResourceLocation item) {}

    /** What wearing {@code pieces} pieces of the set does, one line per effect. */
    public record Tier(int pieces, List<String> lines) {
        public Tier {
            lines = List.copyOf(lines);
        }
    }

    public ArmorSetView {
        pieces = List.copyOf(pieces);
        tiers = List.copyOf(tiers);
    }

    /** The slot {@code item} fills in this set, if it is one of its pieces. */
    public Optional<String> slotOf(ResourceLocation item) {
        for (Piece piece : pieces) {
            if (piece.item().equals(item)) return Optional.of(piece.slot());
        }
        return Optional.empty();
    }
}
