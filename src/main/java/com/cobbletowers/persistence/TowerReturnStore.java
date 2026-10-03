package com.cobbletowers.persistence;

import com.cobbletowers.TowerLog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Disk storage for where each player was before a run took them into the tower (P20). Copies
 * {@link TowerPendingRewardStore}'s exact shape. Written, and flushed, before the first teleport in, so a
 * crash while a player is inside still leaves a way home.
 */
public final class TowerReturnStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_return_points";
    private static final String ENTRIES = "entries";

    private final Map<UUID, ReturnPoint> points = new LinkedHashMap<>();

    public static SavedData.Factory<TowerReturnStore> factory() {
        return new SavedData.Factory<>(TowerReturnStore::new, TowerReturnStore::load, DataFixTypes.LEVEL);
    }

    public static TowerReturnStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public Optional<ReturnPoint> pointFor(UUID player) {
        return Optional.ofNullable(points.get(player));
    }

    /** Records where a player was, replacing any earlier entry: only the latest departure matters. */
    public void put(UUID player, ReturnPoint point) {
        points.put(player, point);
        setDirty();
    }

    public void remove(UUID player) {
        if (points.remove(player) != null) setDirty();
    }

    public int size() {
        return points.size();
    }

    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    static TowerReturnStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerReturnStore store = new TowerReturnStore();
        ListTag list = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        int dropped = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                store.points.put(entry.getUUID("player"), ReturnPoint.fromTag(entry.getCompound("point")));
            } catch (RuntimeException ex) {
                // Unlike the party journal this is safe to drop: a player with no return point is sent to the
                // overworld spawn, which is a worse destination, not a lost Pokemon.
                dropped++;
            }
        }
        if (dropped > 0) TowerLog.error("Dropped {} unreadable tower return point(s); those players go to spawn.", dropped);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, ReturnPoint> entry : points.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", entry.getKey());
            item.put("point", entry.getValue().toTag());
            list.add(item);
        }
        tag.put(ENTRIES, list);
        return tag;
    }
}
