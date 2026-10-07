package com.cobbletowers.network;

import com.cobbletowers.definition.AchievementDefinition;
import com.cobbletowers.definition.AchievementRegistry;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import com.cobbletowers.mastery.LeaderboardRules.Mode;
import com.cobbletowers.mastery.MasteryPerks;
import com.cobbletowers.mastery.MasteryView;
import com.cobbletowers.persistence.TowerLeaderboardStore;
import com.cobbletowers.persistence.TowerMasteryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Builds and sends the mastery screen's payload (P31). The rows are {@link MasteryView}'s own words. */
public final class MasteryScreens {

    public static final String MASTERY_TAB = "mastery";
    private static final int ROWS = 10;

    private MasteryScreens() {}

    /** Whether this player's client can show the screen. A client without it gets chat instead. */
    public static boolean canShow(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, MasteryScreenPayload.TYPE);
    }

    /**
     * The payload for a tower and a tab; an unknown tower falls back to the first loaded one and an unknown tab to the
     * mastery tab, so a modified client can only ever see something valid.
     */
    public static MasteryScreenPayload build(MinecraftServer server, ServerPlayer player, String towerRaw, String tabRaw,
                                             boolean open) {
        List<ResourceLocation> ids = TowerDefinitionRegistry.content().sortedTowerIds();
        TowerMasteryStore store = TowerMasteryStore.get(server);
        List<MasteryScreenPayload.Tower> towers = new ArrayList<>();
        for (ResourceLocation id : ids) {
            TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(id);
            int level = store.progressOf(player.getUUID(), id).level();
            towers.add(new MasteryScreenPayload.Tower(id, tower.displayName(), level, MasteryPerks.rankOf(id, level)));
        }
        ResourceLocation tower = ResourceLocation.tryParse(towerRaw == null ? "" : towerRaw);
        if (tower == null || !ids.contains(tower)) tower = ids.isEmpty() ? null : ids.get(0);
        if (tower == null) {
            return new MasteryScreenPayload(towers, "", MASTERY_TAB, MasteryScreenPayload.Mastery.none(),
                    MasteryScreenPayload.Board.none(), open);
        }

        String tab = tabRaw == null ? MASTERY_TAB : tabRaw.toLowerCase(Locale.ROOT);
        Board board = boardNamed(tab);
        if (board == null) {
            tab = MASTERY_TAB;
            return new MasteryScreenPayload(towers, tower.toString(), tab, masteryOf(store, player, tower),
                    MasteryScreenPayload.Board.none(), open);
        }
        return new MasteryScreenPayload(towers, tower.toString(), tab, MasteryScreenPayload.Mastery.none(),
                boardOf(server, board, tower), open);
    }

    private static Board boardNamed(String name) {
        for (Board board : Board.values()) {
            if (board.name().toLowerCase(Locale.ROOT).equals(name)) return board;
        }
        return null;
    }

    private static MasteryScreenPayload.Mastery masteryOf(TowerMasteryStore store, ServerPlayer player, ResourceLocation tower) {
        TowerMasteryStore.Progress progress = store.progressOf(player.getUUID(), tower);
        List<MasteryScreenPayload.Achievement> achievements = new ArrayList<>();
        for (AchievementDefinition achievement : AchievementRegistry.all()) {
            achievements.add(new MasteryScreenPayload.Achievement(achievement.displayName(), achievement.description(),
                    progress.unlocked().containsKey(achievement.id())));
        }
        String stats = MasteryView.progressLine(tower, progress.level()) + "; " + progress.cyclesCleared() + " cycle(s) cleared, deepest Ascension "
                + progress.ascensionReached();
        return new MasteryScreenPayload.Mastery(stats, MasteryView.perksLine(MasteryPerks.at(tower, progress.level())), achievements);
    }

    private static MasteryScreenPayload.Board boardOf(MinecraftServer server, Board board, ResourceLocation tower) {
        TowerLeaderboardStore store = TowerLeaderboardStore.get(server);
        List<String> solo = rows(board, store.top(new Key(board, tower, board.hasMode() ? Mode.SOLO : Mode.ANY), ROWS));
        List<String> team = board.hasMode() ? rows(board, store.top(new Key(board, tower, Mode.TEAM), ROWS)) : List.of();
        return new MasteryScreenPayload.Board(board.title(), board.hasMode(), solo, team);
    }

    private static List<String> rows(Board board, List<Entry> entries) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) rows.add(MasteryView.row(board, i + 1, entries.get(i)));
        return rows;
    }

    public static void send(ServerPlayer player, MasteryScreenPayload payload) {
        if (canShow(player)) ServerPlayNetworking.send(player, payload);
    }
}
