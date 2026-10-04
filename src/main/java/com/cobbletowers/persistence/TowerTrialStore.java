package com.cobbletowers.persistence;

import com.cobbletowers.trial.StreakRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Each player's trial history (P32): which trials they have attempted and how it went, and their daily streak. Per player,
 * never per run, so it outlives every run. An attempt is recorded when a scored run <b>starts</b>, so abandoning it does not
 * give the player another go.
 */
public final class TowerTrialStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_trials";
    /** Attempts older than the most recent this many are dropped; a trial's board is long gone by then. */
    private static final int MAX_ATTEMPTS = 60;

    /** One attempt at one trial. */
    public record Attempt(String instanceId, UUID runId, boolean finished, int score, int floorsCleared) {}

    private static final class Player {
        StreakRules.State streak = StreakRules.State.NEW;
        /** Whether the player turned the login summary off. */
        boolean quiet;
        final Map<String, Attempt> attempts = new LinkedHashMap<>();
    }

    private final Map<UUID, Player> players = new LinkedHashMap<>();

    public static SavedData.Factory<TowerTrialStore> factory() {
        return new SavedData.Factory<>(TowerTrialStore::new, TowerTrialStore::load, DataFixTypes.LEVEL);
    }

    public static TowerTrialStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public Optional<Attempt> attemptOf(UUID player, String instanceId) {
        Player known = players.get(player);
        return known == null ? Optional.empty() : Optional.ofNullable(known.attempts.get(instanceId));
    }

    /** Every attempt of every player, for the operator's tuning report. */
    public List<Attempt> allAttempts() {
        List<Attempt> all = new java.util.ArrayList<>();
        for (Player player : players.values()) all.addAll(player.attempts.values());
        return all;
    }

    /** Records that a scored run has begun. Returns false if the player already had an attempt (nothing changes). */
    public boolean recordLaunch(UUID player, String instanceId, UUID runId) {
        Player entry = players.computeIfAbsent(player, id -> new Player());
        if (entry.attempts.containsKey(instanceId)) return false;
        entry.attempts.put(instanceId, new Attempt(instanceId, runId, false, 0, 0));
        while (entry.attempts.size() > MAX_ATTEMPTS) entry.attempts.remove(entry.attempts.keySet().iterator().next());
        setDirty();
        return true;
    }

    /** Fills in how an attempt went. Only the run that launched it may. */
    public void recordResult(UUID player, String instanceId, UUID runId, int score, int floorsCleared) {
        Player entry = players.get(player);
        if (entry == null) return;
        Attempt attempt = entry.attempts.get(instanceId);
        if (attempt == null || !attempt.runId().equals(runId) || attempt.finished()) return;
        entry.attempts.put(instanceId, new Attempt(instanceId, runId, true, score, floorsCleared));
        setDirty();
    }

    public StreakRules.State streakOf(UUID player) {
        Player entry = players.get(player);
        return entry == null ? StreakRules.State.NEW : entry.streak;
    }

    public void setStreak(UUID player, StreakRules.State state) {
        players.computeIfAbsent(player, id -> new Player()).streak = state;
        setDirty();
    }

    public boolean isQuiet(UUID player) {
        Player entry = players.get(player);
        return entry != null && entry.quiet;
    }

    public void setQuiet(UUID player, boolean quiet) {
        players.computeIfAbsent(player, id -> new Player()).quiet = quiet;
        setDirty();
    }

    /** An operator's tool: forgets a player's whole trial history. */
    public void reset(UUID player) {
        if (players.remove(player) != null) setDirty();
    }

    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    static TowerTrialStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerTrialStore store = new TowerTrialStore();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            Player player = new Player();
            player.quiet = item.getBoolean("quiet");
            player.streak = new StreakRules.State(item.getLong("last_day"), item.getInt("streak"), item.getInt("freezes"),
                    item.getInt("best"), item.getInt("rewarded"));
            ListTag attempts = item.getList("attempts", Tag.TAG_COMPOUND);
            for (int j = 0; j < attempts.size(); j++) {
                CompoundTag one = attempts.getCompound(j);
                String id = one.getString("instance");
                player.attempts.put(id, new Attempt(id, one.getUUID("run"), one.getBoolean("finished"), one.getInt("score"),
                        one.getInt("floors")));
            }
            store.players.put(item.getUUID("player"), player);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Player> entry : players.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", entry.getKey());
            item.putBoolean("quiet", entry.getValue().quiet);
            StreakRules.State streak = entry.getValue().streak;
            item.putLong("last_day", streak.lastDay());
            item.putInt("streak", streak.streak());
            item.putInt("freezes", streak.freezes());
            item.putInt("best", streak.best());
            item.putInt("rewarded", streak.rewarded());
            ListTag attempts = new ListTag();
            List<Attempt> ordered = new ArrayList<>(entry.getValue().attempts.values());
            for (Attempt attempt : ordered) {
                CompoundTag one = new CompoundTag();
                one.putString("instance", attempt.instanceId());
                one.putUUID("run", attempt.runId());
                one.putBoolean("finished", attempt.finished());
                one.putInt("score", attempt.score());
                one.putInt("floors", attempt.floorsCleared());
                attempts.add(one);
            }
            item.put("attempts", attempts);
            list.add(item);
        }
        tag.put("players", list);
        return tag;
    }
}
