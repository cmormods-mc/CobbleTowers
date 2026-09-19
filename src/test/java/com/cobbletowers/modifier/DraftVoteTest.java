package com.cobbletowers.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TDS #23 as amended: majority, and a tie broken from the seed rather than by a leader. */
class DraftVoteTest {

    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000");
    private static final UUID C = UUID.fromString("cccccccc-0000-0000-0000-000000000000");

    private static Map<UUID, Integer> votes(Object... pairs) {
        Map<UUID, Integer> votes = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) votes.put((UUID) pairs[i], (Integer) pairs[i + 1]);
        return votes;
    }

    @Test
    @DisplayName("a majority wins outright")
    void majority() {
        DraftVote.Result result = DraftVote.resolve(votes(A, 1, B, 1, C, 2), 3, 999L);
        assertEquals(1, result.cardIndex());
        assertFalse(result.byTieBreak(), "two against one needs no tie-break");
        assertEquals(List.of(0, 2, 1), result.tally());
    }

    @Test
    @DisplayName("a tie is broken by the seed, and the same seed always breaks it the same way")
    void tieIsStable() {
        DraftVote.Result first = DraftVote.resolve(votes(A, 0, B, 2), 3, 12345L);
        DraftVote.Result again = DraftVote.resolve(votes(A, 0, B, 2), 3, 12345L);

        assertTrue(first.byTieBreak());
        assertEquals(first.cardIndex(), again.cardIndex(),
                "a tie must survive recovery as the same card, not be re-rolled");
        assertTrue(first.cardIndex() == 0 || first.cardIndex() == 2,
                "the tie-break picks among the cards that actually tied, not any card");
    }

    @Test
    @DisplayName("a draft nobody voted on still resolves")
    void nobodyVoted() {
        DraftVote.Result result = DraftVote.resolve(Map.of(), 3, 77L);
        assertTrue(result.byTieBreak());
        assertTrue(result.cardIndex() >= 0 && result.cardIndex() < 3);
        assertEquals(List.of(0, 0, 0), result.tally(),
                "silence is recorded as silence; the run still moves on");
    }

    @Test
    @DisplayName("a vote for a card that is not on the table is ignored, not trusted")
    void outOfRangeIgnored() {
        // These arrive from a command, so the range check is a real one rather than a formality.
        DraftVote.Result result = DraftVote.resolve(votes(A, 7, B, 0, C, -1), 3, 5L);
        assertEquals(0, result.cardIndex());
        assertEquals(List.of(1, 0, 0), result.tally());
    }

    @Test
    @DisplayName("a negative seed still picks a real card")
    void negativeSeed() {
        // Math.floorMod rather than %: a signed seed with % gives a negative index and throws on a
        // seed that happens to be negative, which is half of them.
        DraftVote.Result result = DraftVote.resolve(Map.of(), 3, Long.MIN_VALUE);
        assertTrue(result.cardIndex() >= 0 && result.cardIndex() < 3, "got " + result.cardIndex());
    }

    @Test
    @DisplayName("every card tying resolves to one of them")
    void allTied() {
        DraftVote.Result result = DraftVote.resolve(votes(A, 0, B, 1, C, 2), 3, 31L);
        assertTrue(result.byTieBreak());
        assertEquals(List.of(1, 1, 1), result.tally());
    }
}
