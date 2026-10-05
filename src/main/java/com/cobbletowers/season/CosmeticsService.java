package com.cobbletowers.season;

import com.cobbletowers.TowerLog;
import com.cobbletowers.persistence.TowerSeasonProgressStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Earning, listing and wearing cosmetics (P36d). {@link #award} is the one door every earned cosmetic goes through (the season track and the
 * club podium both call it), so the rules about a first award live here once: it is recorded, the first title a player earns is worn
 * automatically, the configured console commands run for it, and the tab list is refreshed.
 */
public final class CosmeticsService {

    private CosmeticsService() {}

    /** Records cosmetics for a player. Only ones they did not already have do anything else: no repeat commands, no repeat messages. */
    public static void award(MinecraftServer server, UUID player, Set<String> ids) {
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        Set<String> added = store.addCosmetics(player, ids);
        if (added.isEmpty()) return;

        // A player who wears no title wears the first one they earn; they can change or remove it at any time.
        boolean wearing = Cosmetics.validSelection(store.selectedTitle(player), store.cosmeticsOf(player)).isPresent();
        String nowWearing = null;
        if (!wearing) {
            for (String id : Cosmetics.titlesOf(added)) {
                store.selectTitle(player, id);
                nowWearing = id;
                break;
            }
        }
        store.checkpoint(server);

        ServerPlayer online = server.getPlayerList().getPlayer(player);
        for (String id : added) {
            TowerLog.info("{} earned the cosmetic {}", player, id);
            if (online != null) online.sendSystemMessage(Component.literal("New cosmetic: " + nameOf(id)
                    + (id.equals(nowWearing) ? " (you are wearing it; /tower title to change)" : "")));
            runEarnCommands(server, player, id, online);
        }
        ChatTags.refresh(server, player);
    }

    private static void runEarnCommands(MinecraftServer server, UUID player, String id, ServerPlayer online) {
        List<String> commands = CosmeticsConfig.onEarnCommands();
        if (commands.isEmpty()) return;
        Optional<Cosmetics.Parsed> parsed = Cosmetics.parse(id);
        String name = online != null ? online.getGameProfile().getName()
                : server.getProfileCache() == null ? player.toString()
                : server.getProfileCache().get(player).map(profile -> profile.getName()).orElse(player.toString());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", name);
        values.put("uuid", player.toString());
        values.put("id", id.replace(':', '_'));
        values.put("kind", parsed.map(p -> p.kind().name().toLowerCase(java.util.Locale.ROOT)).orElse("cosmetic"));
        values.put("season", parsed.map(p -> String.valueOf(p.season())).orElse("0"));
        for (String template : commands) {
            String command = Cosmetics.expand(template, values);
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
                TowerLog.info("Ran the cosmetic earn command: {}", command);
            } catch (RuntimeException ex) {
                // A server's own command failing must never undo or block the award.
                TowerLog.error("The cosmetic earn command '{}' failed", command, ex);
            }
        }
    }

    /** A cosmetic's full name, for messages and lists. */
    public static String nameOf(String id) {
        return Cosmetics.parse(id).map(parsed -> Cosmetics.fullName(parsed, Seasons.definition(parsed.season()).name())).orElse(id);
    }

    // ---- what the player sees ---------------------------------------------------------------------------

    /** What {@code /tower cosmetics} says: everything owned, grouped. */
    public static List<String> cosmeticsLines(MinecraftServer server, UUID player) {
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        Set<String> owned = store.cosmeticsOf(player);
        if (owned.isEmpty()) {
            return List.of("You have no cosmetics yet. The season track (/tower season track) and a top-three club finish award them.");
        }
        Optional<String> worn = Cosmetics.validSelection(store.selectedTitle(player), owned);
        List<String> lines = new ArrayList<>();
        lines.add("Your cosmetics:");
        for (Cosmetics.Kind kind : Cosmetics.Kind.values()) {
            List<String> names = new ArrayList<>();
            for (String id : new TreeSet<>(owned)) {
                if (Cosmetics.parse(id).map(parsed -> parsed.kind() == kind).orElse(false)) {
                    names.add(nameOf(id) + (worn.isPresent() && worn.get().equals(id) ? " (worn)" : ""));
                }
            }
            if (!names.isEmpty()) lines.add("  " + label(kind) + ": " + String.join("; ", names));
        }
        lines.add("Only titles can be worn: /tower title.");
        return lines;
    }

    private static String label(Cosmetics.Kind kind) {
        return switch (kind) {
            case TITLE -> "Titles";
            case BADGE -> "Badges";
            case BANNER -> "Banners";
            case CLUB -> "Club marks";
        };
    }

    /** What {@code /tower title} says: the titles owned, numbered, and which is worn. */
    public static List<String> titleLines(MinecraftServer server, UUID player) {
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        List<String> titles = Cosmetics.titlesOf(store.cosmeticsOf(player));
        if (titles.isEmpty()) return List.of("You have no titles yet. Reach step 24 or step 30 of a season track to earn one.");
        Optional<String> worn = Cosmetics.validSelection(store.selectedTitle(player), store.cosmeticsOf(player));
        List<String> lines = new ArrayList<>();
        lines.add("Wearing: " + worn.map(CosmeticsService::nameOf).orElse("no title"));
        for (int i = 0; i < titles.size(); i++) {
            lines.add("  " + (i + 1) + ". " + nameOf(titles.get(i)) + (worn.isPresent() && worn.get().equals(titles.get(i)) ? " (worn)" : ""));
        }
        lines.add("/tower title <number> wears one; /tower title off takes it off.");
        return lines;
    }

    public static String wear(MinecraftServer server, ServerPlayer player, int number) {
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        List<String> titles = Cosmetics.titlesOf(store.cosmeticsOf(player.getUUID()));
        if (number < 1 || number > titles.size()) {
            return titles.isEmpty() ? "You have no titles yet." : "Pick a number from 1 to " + titles.size() + " (see /tower title).";
        }
        store.selectTitle(player.getUUID(), titles.get(number - 1));
        store.checkpoint(server);
        ChatTags.refresh(server, player.getUUID());
        return "You are now wearing: " + nameOf(titles.get(number - 1)) + ".";
    }

    public static String takeOff(MinecraftServer server, ServerPlayer player) {
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        store.selectTitle(player.getUUID(), "");
        store.checkpoint(server);
        ChatTags.refresh(server, player.getUUID());
        return "You are not wearing a title now.";
    }
}
