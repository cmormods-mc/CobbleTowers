package com.cobbletowers.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * What each region has done, per scope ({@code all} or a season id such as {@code s3}): totals and records per region,
 * each player's share for the champions list, and the region each player was last seen in (so a rename or an offline
 * clear still counts for the right side). Champion rows are capped at {@link #MAX_PLAYERS}.
 */
public final class TowerRegionStatsStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_regions";
    public static final String ALL = "all";
    /** A player past this many rows is not listed as a champion, so the file cannot grow without bound. */
    public static final int MAX_PLAYERS = 3000;

    /** A region's numbers in one scope. {@code fastestMs} is 0 until a base cycle is cleared. */
    public record Tally(long clears, long away, long trials, int bestAscension, long fastestMs, int players) {
        public static final Tally NONE = new Tally(0, 0, 0, 0, 0, 0);
    }

    public record Champion(UUID id, String name, long clears, long away) {
        public long points() {
            return clears + away;
        }
    }

    private static final class Mutable {
        long clears, away, trials, fastestMs;
        int bestAscension;
    }

    private static final class Row {
        String name = "";
        long clears, away;
    }

    private final Map<String, Mutable> tallies = new TreeMap<>();
    private final Map<String, Row> rows = new TreeMap<>();
    private final Map<UUID, String> homes = new HashMap<>();

    public static TowerRegionStatsStore get(MinecraftServer server) {
        return open(server, TowerRegionStatsStore::new, TowerRegionStatsStore::load, FILE_ID);
    }

    public Optional<String> homeOf(UUID player) {
        return Optional.ofNullable(homes.get(player));
    }

    public void remember(UUID player, String region) {
        if (!region.equals(homes.put(player, region))) setDirty();
    }

    /** One player's clear for a region. {@code ascension} is the cycle's; {@code millis} counts only for a base cycle. */
    public void recordClear(String scope, String region, UUID player, String name, boolean away, int ascension, long millis) {
        Mutable tally = tallies.computeIfAbsent(scope + "|" + region, k -> new Mutable());
        tally.clears++;
        if (away) tally.away++;
        if (ascension > tally.bestAscension) tally.bestAscension = ascension;
        if (ascension == 0 && millis > 0 && (tally.fastestMs == 0 || millis < tally.fastestMs)) tally.fastestMs = millis;

        String key = scope + "|" + region + "|" + player;
        Row row = rows.get(key);
        if (row == null && rows.size() < MAX_PLAYERS) rows.put(key, row = new Row());
        if (row != null) {
            row.name = name;
            row.clears++;
            if (away) row.away++;
        }
        setDirty();
    }

    public void recordTrial(String scope, String region) {
        tallies.computeIfAbsent(scope + "|" + region, k -> new Mutable()).trials++;
        setDirty();
    }

    public Tally tally(String scope, String region) {
        Mutable t = tallies.get(scope + "|" + region);
        if (t == null) return Tally.NONE;
        String prefix = scope + "|" + region + "|";
        int players = (int) rows.keySet().stream().filter(k -> k.startsWith(prefix)).count();
        return new Tally(t.clears, t.away, t.trials, t.bestAscension, t.fastestMs, players);
    }

    /** The region's best players by clears plus away conquests. */
    public List<Champion> champions(String scope, String region, int limit) {
        String prefix = scope + "|" + region + "|";
        List<Champion> out = new ArrayList<>();
        for (Map.Entry<String, Row> entry : rows.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            Row row = entry.getValue();
            out.add(new Champion(UUID.fromString(entry.getKey().substring(prefix.length())), row.name, row.clears, row.away));
        }
        out.sort(Comparator.comparingLong(Champion::points).reversed().thenComparing(Champion::name));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    static TowerRegionStatsStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerRegionStatsStore store = new TowerRegionStatsStore();
        ListTag tallyList = tag.getList("tallies", Tag.TAG_COMPOUND);
        for (int i = 0; i < tallyList.size(); i++) {
            CompoundTag c = tallyList.getCompound(i);
            Mutable t = new Mutable();
            t.clears = c.getLong("c");
            t.away = c.getLong("a");
            t.trials = c.getLong("t");
            t.fastestMs = c.getLong("f");
            t.bestAscension = c.getInt("b");
            store.tallies.put(c.getString("k"), t);
        }
        ListTag rowList = tag.getList("rows", Tag.TAG_COMPOUND);
        for (int i = 0; i < rowList.size(); i++) {
            CompoundTag c = rowList.getCompound(i);
            Row row = new Row();
            row.name = c.getString("n");
            row.clears = c.getLong("c");
            row.away = c.getLong("a");
            store.rows.put(c.getString("k"), row);
        }
        ListTag homeList = tag.getList("homes", Tag.TAG_COMPOUND);
        for (int i = 0; i < homeList.size(); i++) {
            CompoundTag c = homeList.getCompound(i);
            store.homes.put(c.getUUID("p"), c.getString("r"));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag tallyList = new ListTag();
        for (Map.Entry<String, Mutable> entry : tallies.entrySet()) {
            CompoundTag c = new CompoundTag();
            Mutable t = entry.getValue();
            c.putString("k", entry.getKey());
            c.putLong("c", t.clears);
            c.putLong("a", t.away);
            c.putLong("t", t.trials);
            c.putLong("f", t.fastestMs);
            c.putInt("b", t.bestAscension);
            tallyList.add(c);
        }
        tag.put("tallies", tallyList);
        ListTag rowList = new ListTag();
        for (Map.Entry<String, Row> entry : rows.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("k", entry.getKey());
            c.putString("n", entry.getValue().name);
            c.putLong("c", entry.getValue().clears);
            c.putLong("a", entry.getValue().away);
            rowList.add(c);
        }
        tag.put("rows", rowList);
        ListTag homeList = new ListTag();
        for (Map.Entry<UUID, String> entry : homes.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("p", entry.getKey());
            c.putString("r", entry.getValue());
            homeList.add(c);
        }
        tag.put("homes", homeList);
        return tag;
    }
}
