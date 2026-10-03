package com.cobbletowers.armor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** Builds the tooltip view of a set from its definition (P25). Pure. */
public final class ArmorSetViews {

    private ArmorSetViews() {}

    public static ArmorSetView of(ArmorSetDefinition set) {
        List<ArmorSetView.Piece> pieces = new ArrayList<>();
        for (String slot : ArmorSetDefinition.SLOTS) {
            ResourceLocation item = set.pieces().get(slot);
            if (item != null) pieces.add(new ArmorSetView.Piece(slot, item));
        }

        // Grouped by piece count, in ascending order: a tooltip reads "2 pieces ... full set" top to bottom, and two
        // bonuses at the same count belong under one heading.
        Map<Integer, List<String>> byPieces = new TreeMap<>();
        for (SetBonus bonus : set.bonuses()) {
            byPieces.computeIfAbsent(bonus.pieces(), key -> new ArrayList<>()).addAll(SetBonusDescriber.describe(bonus));
        }
        List<ArmorSetView.Tier> tiers = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> entry : byPieces.entrySet()) {
            if (!entry.getValue().isEmpty()) tiers.add(new ArmorSetView.Tier(entry.getKey(), entry.getValue()));
        }
        return new ArmorSetView(set.id(), set.displayName(), set.color(), pieces, tiers);
    }
}
