package com.cobbletowers.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.persistence.RunOptions;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PracticeTrialRewardTest {

    @Test
    void onlyAnUnscoredTrialIsPractice() {
        assertFalse(PracticeTrialReward.isPractice(RunOptions.NONE));
        assertFalse(PracticeTrialReward.isPractice(new RunOptions(Optional.empty(), Optional.of("daily:2026-10-28"), 5, true)));
        assertTrue(PracticeTrialReward.isPractice(new RunOptions(Optional.empty(), Optional.of("daily:2026-10-28"), 5, false)));
        assertTrue(PracticeTrialReward.isPractice(new RunOptions(Optional.empty(), Optional.of("weekly:2026-W44"), 8, false)));
    }

    @Test
    void theRollIsRepeatable() {
        UUID encounter = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID player = UUID.fromString("66666666-7777-8888-9999-000000000000");
        boolean first = PracticeTrialReward.rollsKey(encounter, player);
        for (int i = 0; i < 20; i++) assertEquals(first, PracticeTrialReward.rollsKey(encounter, player));
    }

    @Test
    void aboutOneInTenRollsAKey() {
        int keys = 0;
        int trials = 20_000;
        UUID player = UUID.fromString("66666666-7777-8888-9999-000000000000");
        for (int i = 0; i < trials; i++) {
            if (PracticeTrialReward.rollsKey(new UUID(i * 7919L, i * 104729L + 1), player)) keys++;
        }
        double rate = keys / (double) trials;
        assertTrue(rate > 0.09 && rate < 0.11, "rate was " + rate);
    }
}
