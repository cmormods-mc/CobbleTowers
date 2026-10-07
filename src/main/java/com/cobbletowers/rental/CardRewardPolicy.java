package com.cobbletowers.rental;

import com.cobbletowers.definition.PlaylistDefinition.CardRewards;
import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.persistence.PartyJournalEntry.LentCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Whether a finished rental run earns real cards, and which (P33b). Pure. The playlist must grant cards, the run must
 * have completed, a trial run must be the scored attempt, the daily allowance must remain and the sets must still be
 * known. One card per Pokemon, up to the rarity cap.
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
     * @param completed the run reached {@code COMPLETED}
     * @param practiceTrial a trial run that was not the scored attempt
     * @param runsToday completed runs already rewarded today
     * @param team the Pokemon the player ran with, per the party journal
     * @param sets looks a set up by id, empty if no longer loaded
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
