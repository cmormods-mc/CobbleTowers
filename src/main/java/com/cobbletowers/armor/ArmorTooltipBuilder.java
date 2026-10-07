package com.cobbletowers.armor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

/**
 * The lines an armor piece adds to its tooltip (P25): set header, piece checklist, bonus lines and a Shift hint. Pure
 * over {@link Component}; only glyphs the stock font draws.
 */
public final class ArmorTooltipBuilder {

    static final String EMBLEM = "◈";     // diamond with a dot
    static final String WORN = "◆";       // filled diamond
    static final String MISSING = "◇";    // hollow diamond
    static final String ACTIVE = "✔";     // heavy tick
    static final String STAR = "★";       // star
    static final String BULLET = "»";     // right guillemet

    private ArmorTooltipBuilder() {}

    /**
     * @param worn what the viewer wears, by slot name
     * @param itemName how to name a piece
     * @param expanded whether to list the pieces (Shift held)
     */
    public static List<Component> build(ArmorSetView set, Map<String, ResourceLocation> worn,
                                        Function<ResourceLocation, Component> itemName, boolean expanded) {
        TextColor accent = TextColor.fromRgb(set.color());
        int total = set.pieces().size();
        int count = wornCount(set, worn);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(header(set, accent, count, total));

        for (ArmorSetView.Tier tier : set.tiers()) {
            boolean lit = count >= tier.pieces();
            lines.add(tierHeading(tier.pieces(), total, lit));
            for (String text : tier.lines()) {
                Style style = lit ? colored(accent) : dim();
                lines.add(Component.literal("   " + BULLET + " ").withStyle(style).append(Component.literal(text).withStyle(style)));
            }
        }

        if (!expanded) {
            lines.add(Component.literal("Hold Shift for pieces").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else {
            lines.add(Component.empty());
            for (ArmorSetView.Piece piece : set.pieces()) {
                boolean has = piece.item().equals(worn.get(piece.slot()));
                Style style = has ? colored(accent) : dim();
                lines.add(Component.literal("  " + (has ? WORN : MISSING) + " ").withStyle(style)
                        .append(itemName.apply(piece.item()).copy().withStyle(style)));
            }
        }
        return lines;
    }

    /** How many of the set's pieces the viewer has on, each in its own slot. */
    public static int wornCount(ArmorSetView set, Map<String, ResourceLocation> worn) {
        int count = 0;
        for (ArmorSetView.Piece piece : set.pieces()) {
            if (piece.item().equals(worn.get(piece.slot()))) count++;
        }
        return count;
    }

    private static Component header(ArmorSetView set, TextColor accent, int count, int total) {
        boolean complete = total > 0 && count >= total;
        MutableComponent line = Component.literal(EMBLEM + " ").withStyle(colored(accent))
                .append(Component.literal(set.name()).withStyle(colored(accent).withBold(true)))
                .append(Component.literal("  " + count + "/" + total)
                        .withStyle(complete ? colored(accent) : Style.EMPTY.withColor(ChatFormatting.GRAY)));
        if (complete) line.append(Component.literal("  " + STAR).withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)));
        return line;
    }

    private static Component tierHeading(int pieces, int total, boolean lit) {
        boolean full = pieces >= total;
        String label = full ? "Full Set" : pieces + (pieces == 1 ? " piece" : " pieces");
        MutableComponent line = Component.literal(lit ? ACTIVE + " " : MISSING + " ")
                .withStyle(lit ? Style.EMPTY.withColor(ChatFormatting.GREEN) : dim());
        if (full) {
            line.append(Component.literal(STAR + " ").withStyle(lit ? Style.EMPTY.withColor(ChatFormatting.GOLD) : dim()));
        }
        return line.append(Component.literal(label)
                .withStyle(lit ? Style.EMPTY.withColor(ChatFormatting.WHITE).withBold(true) : dim()));
    }

    private static Style colored(TextColor color) {
        return Style.EMPTY.withColor(color);
    }

    private static Style dim() {
        return Style.EMPTY.withColor(ChatFormatting.DARK_GRAY);
    }
}
