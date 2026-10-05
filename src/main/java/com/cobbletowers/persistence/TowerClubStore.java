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
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** The clubs (P35) and every player's best regional clear. Plain values; outlives any run. */
public final class TowerClubStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_clubs";

    private final ClubBook book = new ClubBook();

    public static SavedData.Factory<TowerClubStore> factory() {
        return new SavedData.Factory<>(TowerClubStore::new, TowerClubStore::load, DataFixTypes.LEVEL);
    }

    public static TowerClubStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    /** The data. Callers that change it must call {@link #changed()}. */
    public ClubBook book() {
        return book;
    }

    public void changed() {
        setDirty();
    }

    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
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
            store.book.restoreClub(club);
        }
        ListTag bests = tag.getList("bests", Tag.TAG_COMPOUND);
        for (int i = 0; i < bests.size(); i++) {
            store.book.restoreBest(bests.getCompound(i).getUUID("player"), bests.getCompound(i).getInt("score"));
        }
        return store;
    }
}
