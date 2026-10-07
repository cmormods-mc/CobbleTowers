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
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Disk storage for rewards a player earned while offline, one file for the server. Same shape as {@link
 * TowerRunStore}, read directly like {@link CellStateStore} with no in-memory mirror.
 */
public final class TowerPendingRewardStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_pending_rewards";
    private static final String PLAYERS = "players";
    private static final String PLAYER_ID = "player";
    private static final String QUEUE = "queue";

    private final Map<UUID, List<PendingTowerReward>> pending = new LinkedHashMap<>();

    /** The store for this server. Created empty on a world that has never granted a reward. */
    public static TowerPendingRewardStore get(MinecraftServer server) {
        return open(server, TowerPendingRewardStore::new, TowerPendingRewardStore::load, FILE_ID);
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

    /**
     * Queues a reward unless the same grant is already waiting (same run or grant id, item and components), so a
     * replay after a crash is not paid twice. Handed-over rewards are gone, so callers queue before they record.
     */
    public void addIfAbsent(UUID playerId, PendingTowerReward reward) {
        List<PendingTowerReward> queue = pending.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        for (PendingTowerReward waiting : queue) {
            if (waiting.runId().equals(reward.runId()) && waiting.item().equals(reward.item())
                    && waiting.components().equals(reward.components())) return;
        }
        queue.add(reward);
        setDirty();
    }

    /** Removes and returns everything queued for one player. */
    public List<PendingTowerReward> drain(UUID playerId) {
        List<PendingTowerReward> queue = pending.remove(playerId);
        if (queue != null && !queue.isEmpty()) setDirty();
        return queue == null ? List.of() : List.copyOf(queue);
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
