package com.cobbletowers.persistence;

import com.cobbletowers.mastery.LeaderboardRules;
import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import com.cobbletowers.mastery.LeaderboardRules.Member;
import com.cobbletowers.mastery.LeaderboardRules.Mode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The leaderboards (P31, TDS #90): per board, tower and mode, the best entries, each recording the ruleset revision, tower
 * revision and digest it was earned against. Ordering and capping are {@link LeaderboardRules}'; this only stores them.
 */
public final class TowerLeaderboardStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_leaderboards";

    private final Map<Key, List<Entry>> boards = new LinkedHashMap<>();

    public static SavedData.Factory<TowerLeaderboardStore> factory() {
        return new SavedData.Factory<>(TowerLeaderboardStore::new, TowerLeaderboardStore::load, DataFixTypes.LEVEL);
    }

    public static TowerLeaderboardStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    /** Offers an entry to a board; returns its rank (1-based) if it is on the board afterwards, else 0. */
    public int offer(Key key, Entry entry) {
        List<Entry> before = boards.getOrDefault(key, List.of());
        List<Entry> after = LeaderboardRules.offer(key.board(), before, entry);
        if (!after.equals(before)) {
            boards.put(key, after);
            setDirty();
        }
        return after.indexOf(entry) + 1;
    }

    /** The best {@code limit} entries of a board, best first. */
    public List<Entry> top(Key key, int limit) {
        List<Entry> all = boards.getOrDefault(key, List.of());
        return all.subList(0, Math.min(limit, all.size()));
    }

    /** Drops every board the predicate names (old trials), writing only if something went. */
    public void prune(java.util.function.Predicate<Key> stale) {
        if (boards.keySet().removeIf(stale)) setDirty();
    }

    /** An operator's tool, and what a test uses to start clean. */
    public void clear() {
        if (!boards.isEmpty()) {
            boards.clear();
            setDirty();
        }
    }

    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    static TowerLeaderboardStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerLeaderboardStore store = new TowerLeaderboardStore();
        ListTag list = tag.getList("boards", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            try {
                ResourceLocation tower = ResourceLocation.tryParse(item.getString("tower"));
                if (tower == null) continue;
                Key key = new Key(Board.valueOf(item.getString("board")), tower, Mode.valueOf(item.getString("mode")),
                        item.getString("playlist"));
                List<Entry> entries = new ArrayList<>();
                ListTag rows = item.getList("entries", Tag.TAG_COMPOUND);
                for (int j = 0; j < rows.size(); j++) entries.add(entryOf(rows.getCompound(j)));
                store.boards.put(key, List.copyOf(entries));
            } catch (IllegalArgumentException ex) {
                // A board this build does not know (a newer save): skipped, never fatal.
            }
        }
        return store;
    }

    private static Entry entryOf(CompoundTag tag) {
        List<Member> players = new ArrayList<>();
        ListTag members = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            players.add(new Member(member.getUUID("id"), member.getString("name")));
        }
        UUID run = tag.hasUUID("run") ? tag.getUUID("run") : null;
        return new Entry(players, tag.getLong("value"), run, tag.getInt("ascension"), tag.getInt("score"),
                tag.getInt("ruleset_revision"), tag.getInt("tower_revision"), tag.getString("tower_digest"), tag.getLong("at"));
    }

    private static CompoundTag tagOf(Entry entry) {
        CompoundTag tag = new CompoundTag();
        ListTag members = new ListTag();
        for (Member member : entry.players()) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("id", member.id());
            memberTag.putString("name", member.name());
            members.add(memberTag);
        }
        tag.put("players", members);
        if (entry.runId() != null) tag.putUUID("run", entry.runId());
        tag.putLong("value", entry.value());
        tag.putInt("ascension", entry.ascension());
        tag.putInt("score", entry.score());
        tag.putInt("ruleset_revision", entry.rulesetRevision());
        tag.putInt("tower_revision", entry.towerRevision());
        tag.putString("tower_digest", entry.towerDigest());
        tag.putLong("at", entry.at());
        return tag;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Key, List<Entry>> board : boards.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("board", board.getKey().board().name());
            item.putString("tower", board.getKey().tower().toString());
            item.putString("mode", board.getKey().mode().name());
            item.putString("playlist", board.getKey().playlist());
            ListTag rows = new ListTag();
            for (Entry entry : board.getValue()) rows.add(tagOf(entry));
            item.put("entries", rows);
            list.add(item);
        }
        tag.put("boards", list);
        return tag;
    }
}
