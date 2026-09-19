package com.cobbletowers.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RewardTableDefinition;
import com.cobbletowers.encounter.EncounterSeed;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The item and the amount one grant rolls: deterministic, in range, and its own ordinal space. */
class RewardDrawTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }

    private static RewardTableDefinition.Entry entry(String item, int min, int max, int weight) {
        return new RewardTableDefinition.Entry(id(item), min, max, weight);
    }

    @Test
    @DisplayName("the same run, floor and ordinal always pick the same item and amount")
    void deterministic() {
        List<RewardTableDefinition.Entry> pool = List.of(entry("stone", 1, 5, 100), entry("diamond", 1, 3, 50));

        assertEquals(RewardDraw.pickItem(12345L, 3, 0, pool), RewardDraw.pickItem(12345L, 3, 0, pool));
        assertEquals(RewardDraw.rollAmount(12345L, 3, 0, 1, 5), RewardDraw.rollAmount(12345L, 3, 0, 1, 5),
                "a crash cannot reroll a grant already priced (TDS #29)");
    }

    @Test
    @DisplayName("the amount rolled always lands inside its range")
    void withinRange() {
        for (long seed = 0; seed < 200; seed++) {
            int amount = RewardDraw.rollAmount(seed, 1, 0, 4, 9);
            assertTrue(amount >= 4 && amount <= 9, "amount " + amount + " out of [4,9] for seed " + seed);
        }
    }

    @Test
    @DisplayName("a single-entry pool always picks that entry")
    void singleEntry() {
        RewardTableDefinition.Entry only = entry("stone", 1, 1, 100);
        for (int n = 0; n < 20; n++) {
            assertEquals(only, RewardDraw.pickItem(999L, 1, n, List.of(only)));
        }
    }

    @Test
    @DisplayName("weight decides how often an item comes up")
    void weighted() {
        RewardTableDefinition.Entry common = entry("stone", 1, 1, 900);
        RewardTableDefinition.Entry rare = entry("diamond", 1, 1, 100);
        List<RewardTableDefinition.Entry> pool = List.of(common, rare);

        int commonPicked = 0;
        for (int floor = 1; floor <= 200; floor++) {
            if (RewardDraw.pickItem(1L, floor, 0, pool).equals(common)) commonPicked++;
        }
        assertTrue(commonPicked > 150, "a 9:1 weight should be picked far more often, got " + commonPicked);
    }

    @Test
    @DisplayName("a reward's ordinals never collide with any other draw in the codebase")
    void ownOrdinalSpace() {
        long seed = 4242L;
        Set<Long> others = new HashSet<>();
        for (int floor = 1; floor <= 10; floor++) {
            for (int opponent = 0; opponent < 8; opponent++) {
                others.add(EncounterSeed.of(seed, floor, opponent));
            }
            others.add(EncounterSeed.of(seed, floor, 1_000_003));       // BossDraw.BOSS_ORDINAL
            for (int card = 0; card < 3; card++) {
                others.add(EncounterSeed.of(seed, floor, 2_000_029 + card));  // DraftDraw.DRAFT_ORDINAL_BASE
            }
        }
        for (int floor = 1; floor <= 10; floor++) {
            for (int n = 0; n < 10; n++) {
                long itemSeed = EncounterSeed.of(seed, floor, RewardDraw.REWARD_ORDINAL_BASE + n);
                long amountSeed = EncounterSeed.of(seed, floor,
                        RewardDraw.REWARD_ORDINAL_BASE + RewardDraw.AMOUNT_ORDINAL_OFFSET + n);
                assertTrue(others.add(itemSeed), "reward item " + n + " on floor " + floor + " collides");
                assertTrue(others.add(amountSeed), "reward amount " + n + " on floor " + floor + " collides");
            }
        }
    }
}
