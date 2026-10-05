package com.cobbletowers.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.SeasonDefinition;
import com.cobbletowers.mastery.Boards;
import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import com.cobbletowers.mastery.LeaderboardRules.Member;
import com.cobbletowers.mastery.LeaderboardRules.Mode;
import com.cobbletowers.persistence.TowerHallStore;
import com.cobbletowers.persistence.TowerLeaderboardStore;
import com.cobbletowers.persistence.TowerSeasonStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Season-aware boards, the Hall of Fame and the finalisation plan (P36a). */
class SeasonBoardsTest {

    private static final ResourceLocation TIDE = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge");
    private static final ResourceLocation NEUTRAL = ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral");

    private static Entry entry(int n, long value) {
        return new Entry(List.of(new Member(new UUID(0, n), "P" + n)), value, new UUID(1, n), 0, 10, 1, 1, "d", n);
    }

    @Test
    @DisplayName("a key with no season is the all-time board, every old constructor still means all-time, and a season is part of its identity")
    void keys() {
        Key old = new Key(Board.SPEED, TIDE, Mode.SOLO, "monotype");
        assertTrue(old.allTime());
        assertEquals(old, new Key(Board.SPEED, TIDE, Mode.SOLO, "monotype", ""));
        assertEquals(new Key(Board.SPEED, TIDE, Mode.SOLO), new Key(Board.SPEED, TIDE, Mode.SOLO, "", ""));
        Key seasonal = old.inSeason("s2");
        assertFalse(seasonal.allTime());
        assertFalse(old.equals(seasonal), "the same board in a season is a different board");
        assertEquals(old, seasonal.inSeason(""));
    }

    @Test
    @DisplayName("a board stored before seasons existed reads back as all-time, and a seasonal board round-trips with its season")
    void storage() {
        TowerLeaderboardStore store = new TowerLeaderboardStore();
        Key allTime = new Key(Board.DIFFICULTY, TIDE, Mode.SOLO);
        store.offer(allTime, entry(1, 50));
        store.offer(allTime.inSeason("s2"), entry(2, 70));
        CompoundTag saved = store.save(new CompoundTag(), null);

        // A file written before P36a has no "season" field on any board.
        CompoundTag old = saved.copy();
        ListTag boards = old.getList("boards", 10);
        for (int i = 0; i < boards.size(); i++) boards.getCompound(i).remove("season");
        Map<Key, List<Entry>> read = TowerLeaderboardStore.load(old, null).all();
        assertEquals(1, read.size(), "with no season field the two boards collapse onto the one all-time key");
        assertTrue(read.keySet().iterator().next().allTime());

        Map<Key, List<Entry>> restored = TowerLeaderboardStore.load(saved, null).all();
        assertEquals(2, restored.size());
        assertEquals(1, restored.get(allTime).size());
        assertEquals(70, restored.get(allTime.inSeason("s2")).get(0).value());
    }

    @Test
    @DisplayName("a result goes to the all-time board always and to the season's board only while a season is active")
    void offerBoth() {
        TowerLeaderboardStore store = new TowerLeaderboardStore();
        Key key = new Key(Board.DIFFICULTY, TIDE, Mode.SOLO);
        Boards.offerBoth(store, key, entry(1, 40), Optional.of("s1"));
        assertEquals(1, store.top(key, 10).size());
        assertEquals(1, store.top(key.inSeason("s1"), 10).size());

        Boards.offerBoth(store, key, entry(2, 60), Optional.empty());
        assertEquals(2, store.top(key, 10).size(), "an off-season result posts all-time");
        assertEquals(1, store.top(key.inSeason("s1"), 10).size(), "and leaves the frozen season alone");
    }

    @Test
    @DisplayName("only the per-run boards are ever seasonal: Clears (lifetime) and Trial (its own period) never are")
    void seasonalBoards() {
        TowerLeaderboardStore store = new TowerLeaderboardStore();
        for (Board board : Board.values()) {
            Key key = new Key(board, TIDE, board.hasMode() ? Mode.SOLO : Mode.ANY);
            Boards.offerBoth(store, key, entry(1, 5), Optional.of("s1"));
            assertEquals(Boards.seasonal(board) ? 1 : 0, store.top(key.inSeason("s1"), 10).size(), board.name());
            assertEquals(1, store.top(key, 10).size(), board.name() + " always posts all-time");
        }
    }

    @Test
    @DisplayName("the plan takes the top ten of that season's boards only, skips empty ones, and orders them stably")
    void plan() {
        Map<Key, List<Entry>> boards = new LinkedHashMap<>();
        List<Entry> fifteen = new ArrayList<>();
        for (int i = 0; i < 15; i++) fifteen.add(entry(i, 100 - i));
        boards.put(new Key(Board.SPEED, TIDE, Mode.TEAM).inSeason("s1"), fifteen);
        boards.put(new Key(Board.DIFFICULTY, NEUTRAL, Mode.SOLO).inSeason("s1"), List.of(entry(1, 9)));
        boards.put(new Key(Board.DIFFICULTY, TIDE, Mode.SOLO).inSeason("s1"), List.of());
        boards.put(new Key(Board.DIFFICULTY, TIDE, Mode.SOLO), List.of(entry(7, 7)));              // all-time: not this season
        boards.put(new Key(Board.DIFFICULTY, TIDE, Mode.SOLO).inSeason("s2"), List.of(entry(8, 8))); // another season

        HallSeason plan = SeasonFinalizer.plan(new SeasonDefinition(1, "The Rising Tide", Optional.of(TIDE)),
                LocalDate.of(2026, 11, 15), boards);
        assertEquals(2, plan.boards().size());
        assertEquals(Board.SPEED.ordinal() < Board.DIFFICULTY.ordinal() ? Board.SPEED : Board.DIFFICULTY,
                plan.boards().get(0).key().board(), "ordered by board kind");
        HallSeason.Board speed = plan.boards().stream().filter(b -> b.key().board() == Board.SPEED).findFirst().orElseThrow();
        assertEquals(10, speed.entries().size(), "only the top ten are kept");
        assertEquals(100, speed.entries().get(0).value());
        assertTrue(plan.boards().stream().allMatch(b -> b.key().allTime()), "a Hall board is stored without its season");
        assertEquals("The Rising Tide", plan.name());
        assertEquals(Optional.of("cobbletowers:tideforge"), plan.spotlight());
    }

    @Test
    @DisplayName("only seasonal boards older than the newest finished season are pruned")
    void pruning() {
        Key key = new Key(Board.SPEED, TIDE, Mode.SOLO);
        assertFalse(SeasonFinalizer.staleSeasonKey(key, 5), "all-time is never stale");
        assertTrue(SeasonFinalizer.staleSeasonKey(key.inSeason("s3"), 4));
        assertFalse(SeasonFinalizer.staleSeasonKey(key.inSeason("s4"), 4), "the one that just ended stays live");
        assertFalse(SeasonFinalizer.staleSeasonKey(key.inSeason("s5"), 4));
    }

    @Test
    @DisplayName("the Hall is append-only: a season already recorded is never replaced, and it survives a save and load")
    void hall() {
        TowerHallStore hall = new TowerHallStore();
        HallSeason first = new HallSeason(1, "One", Optional.of("cobbletowers:tideforge"), LocalDate.of(2026, 11, 15),
                List.of(new HallSeason.Board(new Key(Board.SPEED, TIDE, Mode.SOLO), List.of(entry(1, 5)))));
        assertTrue(hall.add(first));
        assertFalse(hall.add(new HallSeason(1, "Changed", Optional.empty(), LocalDate.of(2027, 1, 1), List.of())),
                "finalising twice must not change what was recorded");
        assertEquals("One", hall.get(1).orElseThrow().name());
        assertTrue(hall.add(new HallSeason(2, "Two", Optional.empty(), LocalDate.of(2027, 1, 3), List.of())));

        TowerHallStore restored = TowerHallStore.load(hall.save(new CompoundTag(), null), null);
        assertEquals(2, restored.all().size());
        assertEquals(2, restored.latest().orElseThrow().number());
        HallSeason one = restored.get(1).orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 15), one.endedOn());
        assertEquals(Optional.of("cobbletowers:tideforge"), one.spotlight());
        assertEquals(5, one.boards().get(0).entries().get(0).value());
        assertEquals("P1", one.boards().get(0).entries().get(0).players().get(0).name());
    }

    @Test
    @DisplayName("finalisation progress is remembered per season, so a stop between steps resumes at the next one")
    void progress() {
        TowerSeasonStore store = new TowerSeasonStore();
        assertEquals(0, store.stepsDone(1));
        store.stepDone(1, 2);
        assertEquals(2, store.stepsDone(1));
        assertEquals(0, store.stepsDone(2), "another season has not begun");

        TowerSeasonStore restored = TowerSeasonStore.load(store.save(new CompoundTag(), null), null);
        assertEquals(2, restored.stepsDone(1), "progress survives a restart");
        restored.finalized(1);
        assertEquals(1, restored.lastFinalized());
        assertEquals(0, restored.stepsDone(1), "and is cleared once the season is done");
        restored.announcedStart(3);
        restored.announcedStart(2);
        assertEquals(3, restored.lastAnnouncedStart(), "an announcement is never walked backwards");
    }
}
