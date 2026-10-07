package com.cobbletowers.armor;

import com.cobbletowers.CobbleTowers;
import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Worn items to active bonuses (P24). Pure. */
public final class SetBonusResolver {

    /** The most any summed percent may reach, whatever a datapack stacks. */
    static final int PERCENT_CAP = 100;
    static final int VENDOR_DISCOUNT_CAP = 50;

    private SetBonusResolver() {}

    /**
     * @param worn the item in each slot (head, chest, legs, feet); empty slots are absent
     * @param sets every loaded set, in a stable order
     */
    public static ActiveBonuses resolve(Map<String, ResourceLocation> worn, Collection<ArmorSetDefinition> sets) {
        List<ActiveBonuses.Attribute> attributes = new ArrayList<>();
        JsonArray battle = new JsonArray();
        int xp = 0;
        int catchRate = 0;
        int shiny = 0;
        int discount = 0;
        int raidPoints = 0;

        for (ArmorSetDefinition set : sets) {
            int count = wornPieces(worn, set);
            if (count == 0) continue;
            List<SetBonus> bonuses = set.bonuses();
            for (int index = 0; index < bonuses.size(); index++) {
                SetBonus bonus = bonuses.get(index);
                if (bonus.pieces() > count) continue;
                if (bonus instanceof SetBonus.PlayerAttribute attribute) {
                    attributes.add(new ActiveBonuses.Attribute(keyOf(set, index), attribute.attribute(),
                            attribute.operation(), attribute.amount()));
                } else if (bonus instanceof SetBonus.CobblemonModifier modifier) {
                    switch (modifier.kind()) {
                        case XP_PERCENT -> xp += modifier.percent();
                        case CATCH_RATE_PERCENT -> catchRate += modifier.percent();
                        case SHINY_PERCENT -> shiny += modifier.percent();
                    }
                } else if (bonus instanceof SetBonus.BattleEffects effects) {
                    effects.effects().forEach(battle::add);
                } else if (bonus instanceof SetBonus.TowerModifier modifier) {
                    switch (modifier.kind()) {
                        case VENDOR_DISCOUNT_PERCENT -> discount += modifier.percent();
                        case RAID_POINTS_PERCENT -> raidPoints += modifier.percent();
                    }
                }
            }
        }
        return new ActiveBonuses(attributes, Math.min(xp, PERCENT_CAP), Math.min(catchRate, PERCENT_CAP),
                Math.min(shiny, PERCENT_CAP), battle, Math.min(discount, VENDOR_DISCOUNT_CAP),
                Math.min(raidPoints, PERCENT_CAP));
    }

    /**
     * How many distinct slots are filled by that slot's piece of {@code set}; a piece in the wrong slot counts for
     * nothing.
     */
    static int wornPieces(Map<String, ResourceLocation> worn, ArmorSetDefinition set) {
        Set<String> filled = new HashSet<>();
        for (Map.Entry<String, ResourceLocation> entry : worn.entrySet()) {
            ResourceLocation expected = set.pieces().get(entry.getKey());
            if (expected != null && expected.equals(entry.getValue())) filled.add(entry.getKey());
        }
        return filled.size();
    }

    static ResourceLocation keyOf(ArmorSetDefinition set, int index) {
        return CobbleTowers.id("armor_set/" + set.id().getNamespace() + "_" + set.id().getPath().replace('/', '_') + "/" + index);
    }
}
