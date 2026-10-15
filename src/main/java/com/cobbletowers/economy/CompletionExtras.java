package com.cobbletowers.economy;

import com.cobbletowers.api.reward.RewardKind;
import com.cobbletowers.definition.RewardTableDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * The parts of a Tower completion chest that AscensionLib rolls but this mod hands over: armor pieces and CobbleCards booster packs (tickets). The
 * items come from the tower's own reward table (its boss tier), weighted as the table weights them, so a Tideforge tower pays Tideforge pieces and
 * water packs. Pure and seeded by (encounter, player), so a retry names the same items.
 */
public final class CompletionExtras {
    private CompletionExtras() {}

    private static final List<String> SLOTS = List.of("_helmet", "_chestplate", "_leggings", "_boots");

    static boolean isArmor(ResourceLocation item) {
        if (!item.getNamespace().equals("cobbletowers")) return false;
        for (String slot : SLOTS) if (item.getPath().endsWith(slot)) return true;
        return false;
    }

    static boolean isTicket(ResourceLocation item) {
        return item.getNamespace().equals("cobblemon-cards") && item.getPath().startsWith("booster_pack");
    }

    /** {@code armor} distinct pieces then {@code tickets} distinct packs; fewer when the table offers fewer kinds. */
    public static List<ResourceLocation> pick(RewardTableDefinition table, UUID encounterId, UUID player, int armor, int tickets) {
        var random = new Random(encounterId.getMostSignificantBits() * 31 + encounterId.getLeastSignificantBits() * 17
                + player.getMostSignificantBits() * 13 + player.getLeastSignificantBits());
        var out = new ArrayList<ResourceLocation>();
        draw(table, CompletionExtras::isArmor, armor, random, out);
        draw(table, CompletionExtras::isTicket, tickets, random, out);
        return out;
    }

    private static void draw(RewardTableDefinition table, java.util.function.Predicate<ResourceLocation> wanted, int count, Random random,
                             List<ResourceLocation> out) {
        var pool = new ArrayList<RewardTableDefinition.Entry>();
        for (var entry : table.entriesFor(RewardKind.BOSS_DEFEATED)) if (wanted.test(entry.item()) && entry.weight() > 0) pool.add(entry);
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            int total = 0;
            for (var entry : pool) total += entry.weight();
            int roll = random.nextInt(total);
            for (int j = 0; j < pool.size(); j++) {
                roll -= pool.get(j).weight();
                if (roll < 0) {
                    out.add(pool.remove(j).item());
                    break;
                }
            }
        }
    }
}
