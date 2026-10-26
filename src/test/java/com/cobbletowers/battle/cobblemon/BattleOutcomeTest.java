package com.cobbletowers.battle.cobblemon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BattleOutcomeTest {

    private static final UUID PLAYER = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID OPPONENT = UUID.fromString("cccccccc-0000-0000-0000-000000000001");

    @Test
    @DisplayName("a win by the player counts")
    void playerWins() {
        assertTrue(BattleOutcome.playerWon(List.of(PLAYER), PLAYER));
    }

    @Test
    @DisplayName("a win by the opponent is a loss")
    void opponentWins() {
        assertFalse(BattleOutcome.playerWon(List.of(OPPONENT), PLAYER));
    }

    @Test
    @DisplayName("a tie (no winners) counts as a win for the player")
    void tieIsAPlayerWin() {
        assertTrue(BattleOutcome.playerWon(List.of(), PLAYER));
    }
}
