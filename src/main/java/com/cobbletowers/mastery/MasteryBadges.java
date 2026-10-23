package com.cobbletowers.mastery;

import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.mastery.MasteryBadge.Badge;
import com.cobbletowers.persistence.TowerMasteryStore;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** The server side of the mastery badge (P47): which badge a name shows, and pinning it to a tower. */
public final class MasteryBadges {

    private MasteryBadges() {}

    /** The badge a name would show now, ignoring the server switch and the player's own hide flag. */
    public static Optional<Badge> shownFor(MinecraftServer server, UUID player) {
        TowerMasteryStore store = TowerMasteryStore.get(server);
        var towers = TowerDefinitionRegistry.content().sortedTowerIds();
        return MasteryBadge.of(store.standingsOf(player, towers), store.badgePickOf(player));
    }

    /** The result of a pin or unpin: a sentence for the player and whether anything changed. */
    public record Outcome(String message, boolean changed) {}

    /**
     * Pins a badge to a tower ({@code auto} or empty goes back to the highest level). The tower may be a full id or its
     * short name ({@code rootvale}); it must be a loaded tower where the player has a level.
     */
    public static Outcome pin(MinecraftServer server, UUID player, String raw) {
        TowerMasteryStore store = TowerMasteryStore.get(server);
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.equals("auto")) {
            boolean changed = store.setBadgePick(player, Optional.empty());
            if (changed) store.checkpoint(server);
            return new Outcome("Your badge follows your highest mastery level again.", changed);
        }
        Optional<ResourceLocation> tower = resolve(text);
        if (tower.isEmpty()) return new Outcome("There is no tower called " + raw + ".", false);
        int level = store.progressOf(player, tower.get()).level();
        if (level < 1) return new Outcome("You have no mastery level in " + tower.get().getPath() + " yet.", false);
        boolean changed = store.setBadgePick(player, tower);
        if (changed) store.checkpoint(server);
        return new Outcome("Your badge now shows " + tower.get().getPath() + " (level " + level
                + "). The Hall can go back to your highest.", changed);
    }

    private static Optional<ResourceLocation> resolve(String text) {
        for (ResourceLocation id : TowerDefinitionRegistry.content().sortedTowerIds()) {
            if (id.toString().equals(text) || id.getPath().equals(text)) return Optional.of(id);
        }
        return Optional.empty();
    }
}
