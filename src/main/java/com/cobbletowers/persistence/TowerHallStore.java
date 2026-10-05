package com.cobbletowers.persistence;

import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import com.cobbletowers.mastery.LeaderboardRules.Mode;
import com.cobbletowers.season.HallSeason;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The Hall of Fame (P36a): every finished season's frozen top ten, kept forever. Append-only: a season already here is never
 * replaced, so finalising twice (a crash and a resume) cannot change what was recorded.
 */
public final class TowerHallStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_hall";

    private final TreeMap<Integer, HallSeason> seasons = new TreeMap<>();

    public static SavedData.Factory<TowerHallStore> factory() {
        return new SavedData.Factory<>(TowerHallStore::new, TowerHallStore::load, DataFixTypes.LEVEL);
    }

    public static TowerHallStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public boolean has(int number) {
        return seasons.containsKey(number);
    }

    /** Records a season; false (and nothing changed) if that season is already in the Hall. */
    public boolean add(HallSeason season) {
        if (seasons.containsKey(season.number())) return false;
        seasons.put(season.number(), season);
        setDirty();
        return true;
    }

    public Optional<HallSeason> get(int number) {
        return Optional.ofNullable(seasons.get(number));
    }

    public Optional<HallSeason> latest() {
        return seasons.isEmpty() ? Optional.empty() : Optional.of(seasons.lastEntry().getValue());
    }

    public List<HallSeason> all() {
        return List.copyOf(seasons.values());
    }

    /** An operator's reset, and what a test uses to start clean. */
    public void clear() {
        if (!seasons.isEmpty()) {
            seasons.clear();
            setDirty();
        }
    }

    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (HallSeason season : seasons.values()) {
            CompoundTag item = new CompoundTag();
            item.putInt("number", season.number());
            item.putString("name", season.name());
            season.spotlight().ifPresent(spotlight -> item.putString("spotlight", spotlight));
            item.putString("ended_on", season.endedOn().toString());
            ListTag boards = new ListTag();
            for (HallSeason.Board board : season.boards()) {
                CompoundTag b = new CompoundTag();
                b.putString("board", board.key().board().name());
                b.putString("tower", board.key().tower().toString());
                b.putString("mode", board.key().mode().name());
                b.putString("playlist", board.key().playlist());
                ListTag rows = new ListTag();
                for (Entry entry : board.entries()) rows.add(BoardCodec.write(entry));
                b.put("entries", rows);
                boards.add(b);
            }
            item.put("boards", boards);
            list.add(item);
        }
        tag.put("seasons", list);
        return tag;
    }

    public static TowerHallStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerHallStore store = new TowerHallStore();
        ListTag list = tag.getList("seasons", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            try {
                List<HallSeason.Board> boards = new ArrayList<>();
                ListTag stored = item.getList("boards", Tag.TAG_COMPOUND);
                for (int j = 0; j < stored.size(); j++) {
                    CompoundTag b = stored.getCompound(j);
                    ResourceLocation tower = ResourceLocation.tryParse(b.getString("tower"));
                    if (tower == null) continue;
                    List<Entry> entries = new ArrayList<>();
                    ListTag rows = b.getList("entries", Tag.TAG_COMPOUND);
                    for (int k = 0; k < rows.size(); k++) entries.add(BoardCodec.read(rows.getCompound(k)));
                    boards.add(new HallSeason.Board(new Key(Board.valueOf(b.getString("board")), tower,
                            Mode.valueOf(b.getString("mode")), b.getString("playlist")), entries));
                }
                HallSeason season = new HallSeason(item.getInt("number"), item.getString("name"),
                        item.contains("spotlight") ? Optional.of(item.getString("spotlight")) : Optional.empty(),
                        LocalDate.parse(item.getString("ended_on")), boards);
                store.seasons.put(season.number(), season);
            } catch (RuntimeException ex) {
                // A season this build cannot read is skipped, never fatal; the Hall must not stop a server.
            }
        }
        return store;
    }
}
