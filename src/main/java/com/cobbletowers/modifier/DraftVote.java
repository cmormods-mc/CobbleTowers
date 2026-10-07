package com.cobbletowers.modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Who won the vote (TDS #23, amended). There is no party leader, so a tie is broken from the draft's own seed: fixed
 * before anyone votes, unchanged by recovery and ungameable. Pure.
 */
public final class DraftVote {

    private DraftVote() {}

    /**
     * @param cardIndex which card won
     * @param byTieBreak whether the seed decided
     * @param tally votes per card, indexed as the cards are
     */
    public record Result(int cardIndex, boolean byTieBreak, List<Integer> tally) {
        public Result {
            tally = List.copyOf(tally);
        }
    }

    /**
     * Resolves a vote. An unvoted draft still resolves: every card ties at zero and the seed breaks it.
     * @param votes player to card index; out-of-range entries are ignored
     * @param draftSeed the draft's seed, reused to break a tie
     */
    public static Result resolve(Map<?, Integer> votes, int cardCount, long draftSeed) {
        if (cardCount < 1) throw new IllegalArgumentException("a draft needs at least one card");

        int[] counts = new int[cardCount];
        for (Integer choice : votes.values()) {
            if (choice != null && choice >= 0 && choice < cardCount) counts[choice]++;
        }

        int best = 0;
        for (int count : counts) best = Math.max(best, count);

        List<Integer> leaders = new ArrayList<>();
        for (int index = 0; index < cardCount; index++) {
            if (counts[index] == best) leaders.add(index);
        }

        List<Integer> tally = new ArrayList<>(cardCount);
        for (int count : counts) tally.add(count);

        if (leaders.size() == 1) {
            return new Result(leaders.get(0), false, tally);
        }
        // Math.floorMod, not %, because the seed is signed and a negative index would throw here
        // rather than at the call site -- the kind of crash that only shows up on some seeds.
        int chosen = leaders.get((int) Math.floorMod(draftSeed, leaders.size()));
        return new Result(chosen, true, tally);
    }
}
