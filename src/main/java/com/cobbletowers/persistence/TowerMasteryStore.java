package com.cobbletowers.persistence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Each player's mastery per tower (P31): cycles cleared, deepest Ascension and achievements held with when. The
 * mastery level is the number of achievements held. Per player and tower, outliving any run.
 */
public final class TowerMasteryStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_mastery";

    /** A player's standing in one tower. Immutable; the store hands out copies. */
    public record Progress(int cyclesCleared, int ascensionReached, Map<ResourceLocation, Long> unlocked) {
        public static final Progress EMPTY = new Progress(0, 0, Map.of());

        public Progress {
            unlocked = Collections.unmodifiableMap(new LinkedHashMap<>(unlocked));
        }

        /** One level per achievement held. */
        public int level() {
            return unlocked.size();
        }
    }

    private static final class Entry {
        int cycles;
        int depth;
        final Map<ResourceLocation, Long> unlocked = new LinkedHashMap<>();
    }

    private final Map<UUID, Map<ResourceLocation, Entry>> entries = new LinkedHashMap<>();
    /** Players who asked to hide their own mastery badge (P47). */
    private final java.util.Set<UUID> badgeHidden = new java.util.LinkedHashSet<>();
    /** The tower a player pinned their badge to; absent means the highest level wins (P47). */
    private final Map<UUID, ResourceLocation> badgePick = new LinkedHashMap<>();

    public static TowerMasteryStore get(MinecraftServer server) {
        return open(server, TowerMasteryStore::new, TowerMasteryStore::load, FILE_ID);
    }

    public Progress progressOf(UUID player, ResourceLocation tower) {
        Entry entry = entries.getOrDefault(player, Map.of()).get(tower);
        return entry == null ? Progress.EMPTY : new Progress(entry.cycles, entry.depth, entry.unlocked);
    }

    /** Every player's standing in every tower, for the operator's tuning report. */
    public Map<UUID, Map<ResourceLocation, Progress>> all() {
        Map<UUID, Map<ResourceLocation, Progress>> copy = new LinkedHashMap<>();
        entries.forEach((player, byTower) -> {
            Map<ResourceLocation, Progress> mine = new LinkedHashMap<>();
            byTower.forEach((tower, entry) -> mine.put(tower, new Progress(entry.cycles, entry.depth, entry.unlocked)));
            copy.put(player, mine);
        });
        return copy;
    }

    /** One player standing per tower for the badge: the level and when the latest achievement was earned. */
    public Map<ResourceLocation, com.cobbletowers.mastery.MasteryBadge.Standing> standingsOf(UUID player, Iterable<ResourceLocation> towers) {
        Map<ResourceLocation, com.cobbletowers.mastery.MasteryBadge.Standing> out = new LinkedHashMap<>();
        Map<ResourceLocation, Entry> mine = entries.getOrDefault(player, Map.of());
        for (ResourceLocation tower : towers) {
            Entry entry = mine.get(tower);
            if (entry == null || entry.unlocked.isEmpty()) continue;
            long last = entry.unlocked.values().stream().mapToLong(Long::longValue).max().orElse(0L);
            out.put(tower, new com.cobbletowers.mastery.MasteryBadge.Standing(entry.unlocked.size(), last));
        }
        return out;
    }

    public java.util.Optional<ResourceLocation> badgePickOf(UUID player) {
        return java.util.Optional.ofNullable(badgePick.get(player));
    }

    /** Pins a badge to a tower, or back to automatic with an empty pick; returns whether anything changed. */
    public boolean setBadgePick(UUID player, java.util.Optional<ResourceLocation> tower) {
        boolean changed = tower.isPresent() ? !tower.get().equals(badgePick.put(player, tower.get())) : badgePick.remove(player) != null;
        if (changed) setDirty();
        return changed;
    }

    public boolean badgeHidden(UUID player) {
        return badgeHidden.contains(player);
    }

    /** Hides or shows a player's own badge; returns whether anything changed. */
    public boolean setBadgeHidden(UUID player, boolean hidden) {
        boolean changed = hidden ? badgeHidden.add(player) : badgeHidden.remove(player);
        if (changed) setDirty();
        return changed;
    }

    /** One more cycle cleared; returns the new total. */
    public int addCycle(UUID player, ResourceLocation tower) {
        Entry entry = entryFor(player, tower);
        entry.cycles++;
        setDirty();
        return entry.cycles;
    }

    /** Raises the deepest Ascension reached, if {@code ascension} is deeper. */
    public boolean raiseDepth(UUID player, ResourceLocation tower, int ascension) {
        Entry entry = entryFor(player, tower);
        if (ascension <= entry.depth) return false;
        entry.depth = ascension;
        setDirty();
        return true;
    }

    /** Records an achievement; false if the player already holds it. */
    public boolean unlock(UUID player, ResourceLocation tower, ResourceLocation achievement, long at) {
        Entry entry = entryFor(player, tower);
        if (entry.unlocked.containsKey(achievement)) return false;
        entry.unlocked.put(achievement, at);
        setDirty();
        return true;
    }

    /** An operator's tool: forgets everything a player has in a tower. */
    public void reset(UUID player, ResourceLocation tower) {
        Map<ResourceLocation, Entry> byTower = entries.get(player);
        if (byTower != null && byTower.remove(tower) != null) setDirty();
    }

    private Entry entryFor(UUID player, ResourceLocation tower) {
        return entries.computeIfAbsent(player, id -> new LinkedHashMap<>()).computeIfAbsent(tower, id -> new Entry());
    }

    static TowerMasteryStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerMasteryStore store = new TowerMasteryStore();
        ListTag list = tag.getList("progress", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            ResourceLocation tower = ResourceLocation.tryParse(item.getString("tower"));
            if (tower == null) continue;
            Entry entry = store.entryFor(item.getUUID("player"), tower);
            entry.cycles = item.getInt("cycles");
            entry.depth = item.getInt("depth");
            ListTag unlocked = item.getList("unlocked", Tag.TAG_COMPOUND);
            for (int j = 0; j < unlocked.size(); j++) {
                CompoundTag one = unlocked.getCompound(j);
                ResourceLocation id = ResourceLocation.tryParse(one.getString("id"));
                if (id != null) entry.unlocked.put(id, one.getLong("at"));
            }
        }
        ListTag hidden = tag.getList("badge_hidden", Tag.TAG_COMPOUND);
        for (int i = 0; i < hidden.size(); i++) store.badgeHidden.add(hidden.getCompound(i).getUUID("player"));
        ListTag picks = tag.getList("badge_pick", Tag.TAG_COMPOUND);
        for (int i = 0; i < picks.size(); i++) {
            ResourceLocation tower = ResourceLocation.tryParse(picks.getCompound(i).getString("tower"));
            if (tower != null) store.badgePick.put(picks.getCompound(i).getUUID("player"), tower);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Map<ResourceLocation, Entry>> player : entries.entrySet()) {
            for (Map.Entry<ResourceLocation, Entry> byTower : player.getValue().entrySet()) {
                CompoundTag item = new CompoundTag();
                item.putUUID("player", player.getKey());
                item.putString("tower", byTower.getKey().toString());
                item.putInt("cycles", byTower.getValue().cycles);
                item.putInt("depth", byTower.getValue().depth);
                ListTag unlocked = new ListTag();
                for (Map.Entry<ResourceLocation, Long> one : byTower.getValue().unlocked.entrySet()) {
                    CompoundTag oneTag = new CompoundTag();
                    oneTag.putString("id", one.getKey().toString());
                    oneTag.putLong("at", one.getValue());
                    unlocked.add(oneTag);
                }
                item.put("unlocked", unlocked);
                list.add(item);
            }
        }
        tag.put("progress", list);
        ListTag hidden = new ListTag();
        for (UUID player : badgeHidden) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", player);
            hidden.add(item);
        }
        tag.put("badge_hidden", hidden);
        ListTag picks = new ListTag();
        for (Map.Entry<UUID, ResourceLocation> pick : badgePick.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", pick.getKey());
            item.putString("tower", pick.getValue().toString());
            picks.add(item);
        }
        tag.put("badge_pick", picks);
        return tag;
    }
}
