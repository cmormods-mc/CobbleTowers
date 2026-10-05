package com.cobbletowers.persistence;

import com.cobbletowers.echo.Echo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** The recorded Echoes (P35) and the players who opted out of having one. Plain values; outlives any run. */
public final class TowerEchoStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_echoes";

    private final Map<UUID, Echo> echoes = new LinkedHashMap<>();
    private final Set<UUID> optedOut = new HashSet<>();

    public static SavedData.Factory<TowerEchoStore> factory() {
        return new SavedData.Factory<>(TowerEchoStore::new, TowerEchoStore::load, DataFixTypes.LEVEL);
    }

    public static TowerEchoStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    /** Records an Echo, replacing the same player's older one on the same tower. */
    public void add(Echo echo) {
        echoes.values().removeIf(old -> old.owner().equals(echo.owner()) && old.tower().equals(echo.tower()));
        echoes.put(echo.id(), echo);
        setDirty();
    }

    public List<Echo> forTower(ResourceLocation tower) {
        return echoes.values().stream().filter(echo -> echo.tower().equals(tower)).toList();
    }

    public List<Echo> ownedBy(UUID owner) {
        return echoes.values().stream().filter(echo -> echo.owner().equals(owner)).toList();
    }

    public int count() {
        return echoes.size();
    }

    /**
     * Drops the tower's Echoes that no longer belong (P36c): one of the current season (or of the all-time boards, when {@code current} is
     * 0) whose run has left the top ten of {@code currentTopRuns}, and any older than the previous season. The previous season's Echoes
     * are kept whatever happens to its boards: they are the Hall teams that serve until the new season has its own. Returns how many left.
     */
    public int prune(ResourceLocation tower, int current, Set<UUID> currentTopRuns) {
        int before = echoes.size();
        echoes.values().removeIf(echo -> echo.tower().equals(tower) && !com.cobbletowers.echo.EchoPolicy.keeps(echo, current, currentTopRuns));
        if (echoes.size() != before) setDirty();
        return before - echoes.size();
    }

    public Optional<Echo> find(UUID id) {
        return Optional.ofNullable(echoes.get(id));
    }

    public void recordFaced(UUID id) {
        Echo echo = echoes.get(id);
        if (echo == null) return;
        echoes.put(id, echo.withFaced(echo.faced() + 1));
        setDirty();
    }

    /** A duel against this Echo ended: {@code beat} is whether the Echo won. */
    public void recordResult(UUID id, boolean echoWon) {
        Echo echo = echoes.get(id);
        if (echo == null || !echoWon) return;
        echoes.put(id, echo.withBeat(echo.beat() + 1));
        setDirty();
    }

    public boolean isOptedOut(UUID player) {
        return optedOut.contains(player);
    }

    /** Opting out removes the player's Echoes at once; opting back in records nothing until the next top-ten run. */
    public int setOptedOut(UUID player, boolean out) {
        int removed = 0;
        if (out) {
            if (optedOut.add(player)) setDirty();
            int before = echoes.size();
            echoes.values().removeIf(echo -> echo.owner().equals(player));
            removed = before - echoes.size();
            if (removed > 0) setDirty();
        } else if (optedOut.remove(player)) {
            setDirty();
        }
        return removed;
    }

    public void clear() {
        echoes.clear();
        optedOut.clear();
        setDirty();
    }

    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Echo echo : echoes.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", echo.id());
            entry.putUUID("owner", echo.owner());
            entry.putString("name", echo.name());
            entry.putString("tower", echo.tower().toString());
            entry.putUUID("run", echo.runId());
            entry.putLong("at", echo.at());
            entry.putInt("faced", echo.faced());
            entry.putInt("beat", echo.beat());
            entry.putInt("season", echo.season());
            ListTag team = new ListTag();
            for (String member : echo.team()) team.add(StringTag.valueOf(member));
            entry.put("team", team);
            list.add(entry);
        }
        tag.put("echoes", list);
        ListTag out = new ListTag();
        for (UUID id : optedOut) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", id);
            out.add(entry);
        }
        tag.put("opted_out", out);
        return tag;
    }

    public static TowerEchoStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerEchoStore store = new TowerEchoStore();
        ListTag list = tag.getList("echoes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation tower = ResourceLocation.tryParse(entry.getString("tower"));
            if (tower == null) continue;
            List<String> team = new ArrayList<>();
            ListTag members = entry.getList("team", Tag.TAG_STRING);
            for (int j = 0; j < members.size(); j++) team.add(members.getString(j));
            store.echoes.put(entry.getUUID("id"), new Echo(entry.getUUID("id"), entry.getUUID("owner"),
                    entry.getString("name"), tower, entry.getUUID("run"), team, entry.getLong("at"), entry.getInt("faced"),
                    entry.getInt("beat"), entry.getInt("season")));
        }
        ListTag out = tag.getList("opted_out", Tag.TAG_COMPOUND);
        for (int i = 0; i < out.size(); i++) store.optedOut.add(out.getCompound(i).getUUID("player"));
        return store;
    }
}
