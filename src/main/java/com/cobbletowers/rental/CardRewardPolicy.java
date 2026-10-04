package com.cobbletowers.rental;

import com.cobbletowers.definition.PlaylistDefinition.CardRewards;
import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.persistence.PartyJournalEntry.LentCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Whether a finished rental run earns real cards, and which (P33b). Pure: everything it needs is passed in, so every rule is a test.
 *
 * <p>The rules, in the order they are judged: the playlist must grant cards at all; the run must have <b>completed</b> (a cash-out, a
 * wipe and an abandon earn none); a trial run must be the scored attempt (a practice run cannot be farmed); the player must not have
 * used up the playlist's cards-per-day allowance; and the player must have run with Pokemon whose sets are still known. A card is made
 * for each of those Pokemon, never above the playlist's rarity cap.
 */
public final class CardRewardPolicy {

    public enum Outcome { GRANT, DISABLED, NOT_COMPLETED, PRACTICE_TRIAL, DAILY_LIMIT, NO_TEAM }

    /** One card to hand over, with the set it came from and the words a player reads. */
    public record Granted(RentalSetDefinition set, RentalCards.Spec card, String label) {}

    public record Decision(Outcome outcome, List<Granted> cards) {
        public Decision {
            cards = List.copyOf(cards);
        }

        public boolean grants() {
            return outcome == Outcome.GRANT;
        }
    }

    private CardRewardPolicy() {}

    /**
     * @param completed     whether the run reached {@code COMPLETED}
     * @param practiceTrial whether the run was a trial run that was not the player's scored attempt
     * @param runsToday     how many completed runs have already earned this player cards today
     * @param team          the Pokemon the player ran with, as the party journal recorded them
     * @param sets          looks a set up by id, empty if it is no longer loaded
     */
    public static Decision decide(CardRewards config, boolean completed, boolean practiceTrial, int runsToday, List<LentCard> team,
                                  Function<String, Optional<RentalSetDefinition>> sets) {
        if (!config.enabled()) return new Decision(Outcome.DISABLED, List.of());
        if (!completed) return new Decision(Outcome.NOT_COMPLETED, List.of());
        if (practiceTrial) return new Decision(Outcome.PRACTICE_TRIAL, List.of());
        if (runsToday >= config.runsPerDay()) return new Decision(Outcome.DAILY_LIMIT, List.of());
        List<Granted> cards = new ArrayList<>();
        for (LentCard lent : team) {
            Optional<RentalSetDefinition> set = sets.apply(lent.set());
            if (set.isEmpty()) continue;
            RentalCards.Spec card = RentalCards.of(set.get(), lent.god(), config.maxRarity());
            cards.add(new Granted(set.get(), card, RentalCards.label(set.get(), card)));
        }
        return cards.isEmpty() ? new Decision(Outcome.NO_TEAM, List.of()) : new Decision(Outcome.GRANT, cards);
    }
}
