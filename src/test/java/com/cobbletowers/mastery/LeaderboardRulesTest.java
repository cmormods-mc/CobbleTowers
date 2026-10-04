package com.cobbletowers.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Member;
import com.cobbletowers.mastery.LeaderboardRules.Mode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeaderboardRulesTest {

    private static Entry entry(UUID run, long value, long at) {
        return new Entry(List.of(new Member(UUID.randomUUID(), "P")), value, run, 0, 10, 1, 1, "d", at);
    }

    private static Entry clears(UUID player, long value, long at) {
        return new Entry(List.of(new Member(player, "P")), value, null, 0, 0, 1, 1, "d", at);
    }

    @Test
    @DisplayName("higher is better on depth, difficulty and clears; lower is better on speed")
    void direction() {
        assertTrue(LeaderboardRules.beats(Board.ASCENSION, entry(UUID.randomUUID(), 9, 5), entry(UUID.randomUUID(), 4, 1)));
        assertTrue(LeaderboardRules.beats(Board.SPEED, entry(UUID.randomUUID(), 300_000, 5), entry(UUID.randomUUID(), 400_000, 1)));
        assertTrue(Board.SPEED.lowerIsBetter());
        assertTrue(!Board.DIFFICULTY.lowerIsBetter() && !Board.CLEARS.lowerIsBetter());
    }

    @Test
    @DisplayName("a tie goes to whoever got there first")
    void tieBreak() {
        assertTrue(LeaderboardRules.beats(Board.ASCENSION, entry(UUID.randomUUID(), 5, 1), entry(UUID.randomUUID(), 5, 2)));
        assertTrue(!LeaderboardRules.beats(Board.ASCENSION, entry(UUID.randomUUID(), 5, 3), entry(UUID.randomUUID(), 5, 2)));
    }

    @Test
    @DisplayName("an offered entry is sorted into place, best first")
    void sorted() {
        List<Entry> board = List.of();
        for (long value : new long[] {3, 9, 5, 7}) board = LeaderboardRules.offer(Board.ASCENSION, board, entry(UUID.randomUUID(), value, value));
        assertEquals(List.of(9L, 7L, 5L, 3L), board.stream().map(Entry::value).toList());
        List<Entry> speed = List.of();
        for (long value : new long[] {500, 200, 900}) speed = LeaderboardRules.offer(Board.SPEED, speed, entry(UUID.randomUUID(), value, value));
        assertEquals(List.of(200L, 500L, 900L), speed.stream().map(Entry::value).toList());
    }

    @Test
    @DisplayName("a board keeps at most fifty entries and drops the worst")
    void capped() {
        List<Entry> board = List.of();
        for (int i = 1; i <= 80; i++) board = LeaderboardRules.offer(Board.DIFFICULTY, board, entry(UUID.randomUUID(), i, i));
        assertEquals(LeaderboardRules.CAPACITY, board.size());
        assertEquals(80L, board.get(0).value());
        assertEquals(31L, board.get(board.size() - 1).value());
    }

    @Test
    @DisplayName("a run holds one entry per board: a better result replaces it, a worse or equal one does not")
    void oneEntryPerRun() {
        UUID run = UUID.randomUUID();
        List<Entry> board = LeaderboardRules.offer(Board.ASCENSION, List.of(), entry(run, 4, 1));
        board = LeaderboardRules.offer(Board.ASCENSION, board, entry(run, 7, 2));
        assertEquals(List.of(7L), board.stream().map(Entry::value).toList());
        board = LeaderboardRules.offer(Board.ASCENSION, board, entry(run, 3, 3));
        board = LeaderboardRules.offer(Board.ASCENSION, board, entry(run, 7, 4));
        assertEquals(1, board.size());
        assertEquals(7L, board.get(0).value());
        assertEquals(2L, board.get(0).at(), "the earlier entry stays");
        List<Entry> speed = LeaderboardRules.offer(Board.SPEED, List.of(), entry(run, 900, 1));
        speed = LeaderboardRules.offer(Board.SPEED, speed, entry(run, 400, 2));
        assertEquals(List.of(400L), speed.stream().map(Entry::value).toList(), "lower replaces on the speed board");
    }

    @Test
    @DisplayName("the clears board keeps one entry per player, since it has no run")
    void clearsPerPlayer() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<Entry> board = LeaderboardRules.offer(Board.CLEARS, List.of(), clears(a, 1, 1));
        board = LeaderboardRules.offer(Board.CLEARS, board, clears(b, 3, 2));
        board = LeaderboardRules.offer(Board.CLEARS, board, clears(a, 2, 3));
        assertEquals(2, board.size());
        assertEquals(List.of(3L, 2L), board.stream().map(Entry::value).toList());
    }

    @Test
    @DisplayName("a board without a mode ignores the mode in its key")
    void keys() {
        ResourceLocation tower = ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral");
        assertEquals(new LeaderboardRules.Key(Board.CLEARS, tower, Mode.ANY), new LeaderboardRules.Key(Board.CLEARS, tower, Mode.SOLO));
        assertTrue(!new LeaderboardRules.Key(Board.SPEED, tower, Mode.SOLO).equals(new LeaderboardRules.Key(Board.SPEED, tower, Mode.TEAM)));
        assertEquals(Mode.SOLO, LeaderboardRules.modeOf(true));
        assertEquals(Mode.TEAM, LeaderboardRules.modeOf(false));
        assertEquals(new ArrayList<>(), new ArrayList<>(List.of()));
    }
}
