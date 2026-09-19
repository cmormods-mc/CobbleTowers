package com.cobbletowers.modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Who won the vote (TDS #23, as amended).
 *
 * <p><b>The amendment.</b> #23 says a tie "resolves to party leader". There is no party leader: the
 * LOCKED vision line says a run is owned by its own UUID <i>"not by a leader or initiator"</i>, and
 * #12 repeats it. A tie is therefore broken from the draft's own seed.
 *
 * <p>That is also the better rule. It needs no new concept; it inherits #29's property that a crash
 * cannot change the outcome, so a tie survives recovery as the same card; and it cannot be gamed,
 * because the tie-break is fixed before anybody votes. A leader rule would have handed one player a
 * silent advantage for a whole run.
 *
 * <p>Pure. Every rule here is a unit test.
 */
public final class DraftVote {

    private DraftVote() {}

    /**
     * @param cardIndex     which card won
     * @param byTieBreak    whether the seed decided it rather than a majority
     * @param tally         votes per card, indexed as the cards are
     */
    public record Result(int cardIndex, boolean byTieBreak, List<Integer> tally) {
        public Result {
            tally = List.copyOf(tally);
        }
    }

    /**
     * Resolves a vote.
     *
     * <p><b>An unvoted draft still resolves.</b> Every card sits at zero, which is a tie, which the
     * seed breaks -- so a party that says nothing gets a card rather than a run that cannot move.
     * The alternative is a floor held open by silence, and P7 already settled that one AFK player
     * must not be able to stop three others.
     *
     * @param votes     player to card index; entries outside {@code cardCount} are ignored rather
     *                  than trusted, since they arrive from a command
     * @param draftSeed the seed this draft was drawn from, reused to break a tie
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
