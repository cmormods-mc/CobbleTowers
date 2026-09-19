package com.cobbletowers.persistence;

import com.cobbletowers.TowerLog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Disk storage for rewards a player earned but was not online to receive, attached to the overworld's
 * data storage so one file covers the server rather than one per dimension.
 *
 * <p>Copies {@link TowerRunStore}'s exact shape, which itself follows CobbleRaids'
 * {@code PendingRewardStore} (P2). No separate in-memory mirror: {@link CellStateStore} is this
 * codebase's own precedent for reading a {@code SavedData} directly, and nothing here needs the
 * reverse player-to-run index {@link com.cobbletowers.runtime.TowerRuns} keeps for a different reason.
 */
public final class TowerPendingRewardStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_pending_rewards";
    private static final String PLAYERS = "players";
    private static final String PLAYER_ID = "player";
    private static final String QUEUE = "queue";

    private final Map<UUID, List<PendingTowerReward>> pending = new LinkedHashMap<>();

    public static SavedData.Factory<TowerPendingRewardStore> factory() {
        return new SavedData.Factory<>(TowerPendingRewardStore::new, TowerPendingRewardStore::load, DataFixTypes.LEVEL);
    }

    /** The store for this server. Created empty on a world that has never granted a reward. */
    public static TowerPendingRewardStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    /** One player's queue, front of queue first. A copy: callers must not mutate the store's list. */
    public List<PendingTowerReward> queueFor(UUID playerId) {
        return List.copyOf(pending.getOrDefault(playerId, List.of()));
    }

    /** Queues one reward and marks the file dirty, to be written at the next autosave. */
    public void add(UUID playerId, PendingTowerReward reward) {
        pending.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(reward);
        setDirty();
    }

    /** Removes and returns everything queued for one player. */
    public List<PendingTowerReward> drain(UUID playerId) {
        List<PendingTowerReward> queue = pending.remove(playerId);
        if (queue != null && !queue.isEmpty()) setDirty();
        return queue == null ? List.of() : List.copyOf(queue);
    }

    /**
     * Writes the file to disk immediately, the same "flush now" reasoning {@link TowerRunStore}'s own
     * {@code checkpoint} gives: a reward queued and then lost to a crash before the next autosave is a
     * reward a player was already told they earned.
     */
    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    /** Package-private rather than private so a test can round-trip the file without a server. */
    static TowerPendingRewardStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerPendingRewardStore store = new TowerPendingRewardStore();
        ListTag players = tag.getList(PLAYERS, Tag.TAG_COMPOUND);
        int dropped = 0;
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            UUID playerId = playerTag.getUUID(PLAYER_ID);
            List<PendingTowerReward> queue = new ArrayList<>();
            ListTag entries = playerTag.getList(QUEUE, Tag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) {
                try {
                    queue.add(PendingTowerReward.fromTag(entries.getCompound(j)));
                } catch (RuntimeException ex) {
                    dropped++;
                }
            }
            if (!queue.isEmpty()) store.pending.put(playerId, queue);
        }
        if (dropped > 0) {
            TowerLog.error("Dropped {} unreadable pending tower reward(s).", dropped);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, List<PendingTowerReward>> entry : pending.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(PLAYER_ID, entry.getKey());
            ListTag queue = new ListTag();
            for (PendingTowerReward reward : entry.getValue()) queue.add(reward.toTag());
            playerTag.put(QUEUE, queue);
            players.add(playerTag);
        }
        tag.put(PLAYERS, players);
        return tag;
    }
}
