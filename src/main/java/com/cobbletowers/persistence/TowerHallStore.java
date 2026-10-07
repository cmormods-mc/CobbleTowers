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

/**
 * The Hall of Fame (P36a): every finished season's frozen top ten, append-only, so finalising twice (crash and
 * resume) changes nothing.
 */
public final class TowerHallStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_hall";

    private final TreeMap<Integer, HallSeason> seasons = new TreeMap<>();

    public static TowerHallStore get(MinecraftServer server) {
        return open(server, TowerHallStore::new, TowerHallStore::load, FILE_ID);
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
            ListTag clubs = new ListTag();
            for (HallSeason.Club club : season.clubs()) {
                CompoundTag c = new CompoundTag();
                c.putString("name", club.name());
                c.putString("tag", club.tag());
                c.putString("banner", club.banner());
                c.putInt("score", club.score());
                ListTag names = new ListTag();
                for (String member : club.members()) names.add(net.minecraft.nbt.StringTag.valueOf(member));
                c.put("members", names);
                clubs.add(c);
            }
            item.put("clubs", clubs);
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
                List<HallSeason.Club> clubs = new ArrayList<>();
                ListTag storedClubs = item.getList("clubs", Tag.TAG_COMPOUND);
                for (int j = 0; j < storedClubs.size(); j++) {
                    CompoundTag c = storedClubs.getCompound(j);
                    List<String> members = new ArrayList<>();
                    ListTag names = c.getList("members", Tag.TAG_STRING);
                    for (int k = 0; k < names.size(); k++) members.add(names.getString(k));
                    clubs.add(new HallSeason.Club(c.getString("name"), c.getString("tag"), c.getString("banner"), c.getInt("score"), members));
                }
                HallSeason season = new HallSeason(item.getInt("number"), item.getString("name"),
                        item.contains("spotlight") ? Optional.of(item.getString("spotlight")) : Optional.empty(),
                        LocalDate.parse(item.getString("ended_on")), boards, clubs);
                store.seasons.put(season.number(), season);
            } catch (RuntimeException ex) {
                // A season this build cannot read is skipped, never fatal; the Hall must not stop a server.
            }
        }
        return store;
    }
}
