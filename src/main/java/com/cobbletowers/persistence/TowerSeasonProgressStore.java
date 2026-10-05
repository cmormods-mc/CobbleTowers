package com.cobbletowers.persistence;

import com.cobbletowers.season.SeasonPoints.Progress;
import com.cobbletowers.season.SeasonPoints.Source;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Each player's season points and track progress (P36b), and the cosmetics they have earned. Points belong to one season: a record
 * for another season reads as nothing. Cosmetics are permanent and never touched by a new season.
 */
public final class TowerSeasonProgressStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_season_progress";

    private record Entry(int season, Progress progress) {}

    private final Map<UUID, Entry> progress = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> cosmetics = new LinkedHashMap<>();

    public static SavedData.Factory<TowerSeasonProgressStore> factory() {
        return new SavedData.Factory<>(TowerSeasonProgressStore::new, TowerSeasonProgressStore::load, DataFixTypes.LEVEL);
    }

    public static TowerSeasonProgressStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    /** The player's tally for {@code season}; empty if they have none, or only one from another season. */
    public Progress of(UUID player, int season) {
        Entry entry = progress.get(player);
        return entry != null && entry.season() == season ? entry.progress() : Progress.EMPTY;
    }

    public void put(UUID player, int season, Progress next) {
        progress.put(player, new Entry(season, next));
        setDirty();
    }

    /** Every cosmetic the player has earned, such as {@code s1:banner_1}. */
    public Set<String> cosmeticsOf(UUID player) {
        return Set.copyOf(cosmetics.getOrDefault(player, Set.of()));
    }

    public void addCosmetics(UUID player, Set<String> earned) {
        if (earned.isEmpty()) return;
        cosmetics.computeIfAbsent(player, id -> new TreeSet<>()).addAll(earned);
        setDirty();
    }

    /** An operator's reset, and what a test uses to start clean. */
    public void clear() {
        progress.clear();
        cosmetics.clear();
        setDirty();
    }

    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> item : progress.entrySet()) {
            Progress p = item.getValue().progress();
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", item.getKey());
            entry.putInt("season", item.getValue().season());
            entry.putInt("total", p.total());
            entry.putInt("steps", p.steps());
            entry.putString("day", p.day());
            entry.putInt("day_total", p.dayTotal());
            CompoundTag counts = new CompoundTag();
            for (Map.Entry<Source, Integer> count : p.dayCounts().entrySet()) counts.putInt(count.getKey().name(), count.getValue());
            entry.put("day_counts", counts);
            ListTag once = new ListTag();
            for (String key : new TreeSet<>(p.once())) once.add(StringTag.valueOf(key));
            entry.put("once", once);
            list.add(entry);
        }
        tag.put("progress", list);
        ListTag earned = new ListTag();
        for (Map.Entry<UUID, Set<String>> item : cosmetics.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", item.getKey());
            ListTag names = new ListTag();
            for (String name : item.getValue()) names.add(StringTag.valueOf(name));
            entry.put("names", names);
            earned.add(entry);
        }
        tag.put("cosmetics", earned);
        return tag;
    }

    public static TowerSeasonProgressStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerSeasonProgressStore store = new TowerSeasonProgressStore();
        ListTag list = tag.getList("progress", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Map<Source, Integer> counts = new EnumMap<>(Source.class);
            CompoundTag stored = entry.getCompound("day_counts");
            for (Source source : Source.values()) if (stored.contains(source.name())) counts.put(source, stored.getInt(source.name()));
            Set<String> once = new HashSet<>();
            ListTag keys = entry.getList("once", Tag.TAG_STRING);
            for (int j = 0; j < keys.size(); j++) once.add(keys.getString(j));
            store.progress.put(entry.getUUID("player"), new Entry(entry.getInt("season"),
                    new Progress(entry.getInt("total"), entry.getInt("steps"), entry.getString("day"), entry.getInt("day_total"),
                            counts, once)));
        }
        ListTag earned = tag.getList("cosmetics", Tag.TAG_COMPOUND);
        for (int i = 0; i < earned.size(); i++) {
            CompoundTag entry = earned.getCompound(i);
            Set<String> names = new TreeSet<>();
            ListTag stored = entry.getList("names", Tag.TAG_STRING);
            for (int j = 0; j < stored.size(); j++) names.add(stored.getString(j));
            store.cosmetics.put(entry.getUUID("player"), names);
        }
        return store;
    }
}
