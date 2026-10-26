package com.cobbletowers.battle.cobblemon;

import java.util.Collection;
import java.util.UUID;

/** Who a finished floor battle belongs to. Cobblemon-free so the rule is tested. */
final class BattleOutcome {

    private BattleOutcome() {}

    /** Nobody won: both sides fainted together. */
    static boolean isTie(Collection<UUID> winners) {
        return winners.isEmpty();
    }

    /** The player won, or the battle was a tie (no winners: both sides fainted together), which counts for the player. */
    static boolean playerWon(Collection<UUID> winners, UUID player) {
        return isTie(winners) || winners.contains(player);
    }
}
