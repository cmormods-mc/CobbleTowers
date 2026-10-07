package com.cobbletowers.persistence;

import com.cobbletowers.club.ClubBook;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/** The clubs (P35) and every player's best regional clear. Plain values; outlives any run. */
public final class TowerClubStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_clubs";

    private final ClubBook book = new ClubBook();

    public static TowerClubStore get(MinecraftServer server) {
        return open(server, TowerClubStore::new, TowerClubStore::load, FILE_ID);
    }

    /** The data. Callers that change it must call {@link #changed()}. */
    public ClubBook book() {
        return book;
    }

    public void changed() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag clubs = new ListTag();
        for (ClubBook.Club club : book.all()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", club.name());
            entry.putString("tag", club.tag());
            entry.putString("banner", club.banner());
            entry.putUUID("owner", club.owner());
            entry.putLong("created", club.createdAt());
            entry.putString("week", club.weekKey());
            entry.putInt("week_clears", club.weekClears());
            ListTag members = new ListTag();
            for (Map.Entry<UUID, String> member : club.members().entrySet()) {
                CompoundTag m = new CompoundTag();
                m.putUUID("id", member.getKey());
                m.putString("name", member.getValue());
                members.add(m);
            }
            entry.put("members", members);
            ListTag claimed = new ListTag();
            for (UUID id : club.claimed()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", id);
                claimed.add(c);
            }
            entry.put("claimed", claimed);
            ListTag unlocked = new ListTag();
            for (String banner : new java.util.TreeSet<>(club.unlockedBanners())) unlocked.add(net.minecraft.nbt.StringTag.valueOf(banner));
            entry.put("unlocked_banners", unlocked);
            ListTag honors = new ListTag();
            for (String honor : club.honors()) honors.add(net.minecraft.nbt.StringTag.valueOf(honor));
            entry.put("honors", honors);
            clubs.add(entry);
        }
        tag.put("clubs", clubs);
        ListTag bests = new ListTag();
        for (Map.Entry<UUID, Integer> best : book.bests().entrySet()) {
            CompoundTag b = new CompoundTag();
            b.putUUID("player", best.getKey());
            b.putInt("score", best.getValue());
            bests.add(b);
        }
        tag.put("bests", bests);
        ListTag seasonBests = new ListTag();
        for (Map.Entry<Integer, Map<UUID, Integer>> season : book.seasonBests().entrySet()) {
            for (Map.Entry<UUID, Integer> best : season.getValue().entrySet()) {
                CompoundTag b = new CompoundTag();
                b.putInt("season", season.getKey());
                b.putUUID("player", best.getKey());
                b.putInt("score", best.getValue());
                seasonBests.add(b);
            }
        }
        tag.put("season_bests", seasonBests);
        return tag;
    }

    public static TowerClubStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerClubStore store = new TowerClubStore();
        ListTag clubs = tag.getList("clubs", Tag.TAG_COMPOUND);
        for (int i = 0; i < clubs.size(); i++) {
            CompoundTag entry = clubs.getCompound(i);
            Map<UUID, String> members = new LinkedHashMap<>();
            ListTag stored = entry.getList("members", Tag.TAG_COMPOUND);
            for (int j = 0; j < stored.size(); j++) {
                members.put(stored.getCompound(j).getUUID("id"), stored.getCompound(j).getString("name"));
            }
            if (members.isEmpty()) continue;
            Set<UUID> claimed = new HashSet<>();
            ListTag claims = entry.getList("claimed", Tag.TAG_COMPOUND);
            for (int j = 0; j < claims.size(); j++) claimed.add(claims.getCompound(j).getUUID("id"));
            UUID owner = entry.getUUID("owner");
            String ownerName = members.getOrDefault(owner, "?");
            ClubBook.Club club = new ClubBook.Club(entry.getString("name"), entry.getString("tag"), owner, ownerName,
                    entry.getLong("created"));
            club.restore(entry.getString("banner"), entry.getString("week"), entry.getInt("week_clears"), claimed, members);
            Set<String> unlocked = new HashSet<>();
            ListTag unlockedTag = entry.getList("unlocked_banners", Tag.TAG_STRING);
            for (int j = 0; j < unlockedTag.size(); j++) unlocked.add(unlockedTag.getString(j));
            java.util.List<String> honors = new java.util.ArrayList<>();
            ListTag honorTag = entry.getList("honors", Tag.TAG_STRING);
            for (int j = 0; j < honorTag.size(); j++) honors.add(honorTag.getString(j));
            club.restoreHonors(unlocked, honors);
            store.book.restoreClub(club);
        }
        ListTag bests = tag.getList("bests", Tag.TAG_COMPOUND);
        for (int i = 0; i < bests.size(); i++) {
            store.book.restoreBest(bests.getCompound(i).getUUID("player"), bests.getCompound(i).getInt("score"));
        }
        ListTag seasonBests = tag.getList("season_bests", Tag.TAG_COMPOUND);
        for (int i = 0; i < seasonBests.size(); i++) {
            CompoundTag b = seasonBests.getCompound(i);
            store.book.restoreSeasonBest(b.getInt("season"), b.getUUID("player"), b.getInt("score"));
        }
        return store;
    }
}
