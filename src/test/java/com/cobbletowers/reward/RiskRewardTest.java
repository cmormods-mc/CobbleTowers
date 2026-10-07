package com.cobbletowers.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.cobbletowers.api.modifier.RiskTier;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class RiskRewardTest {
    @Test
    void tiersAddUpAndDoNotCompound() {
        assertEquals(0, RiskReward.bonusPercent(List.of()));
        assertEquals(3 + 8 + 15 + 15, RiskReward.bonusPercent(List.of(RiskTier.MINOR, RiskTier.MODERATE, RiskTier.SEVERE, RiskTier.SEVERE)));
    }

    @Test
    void scaleRoundsDownAndNeverShrinks() {
        assertEquals(10, RiskReward.scale(10, 0));
        assertEquals(11, RiskReward.scale(10, 15));
        assertEquals(1, RiskReward.scale(1, 15));
        assertEquals(0, RiskReward.scale(0, 40));
    }

    @Test
    void guaranteedItemsAreNeverScaled() {
        var item = ResourceLocation.fromNamespaceAndPath("cobblemon", "rare_candy");
        var rolled = new RewardValuation.Grant(item, 20);
        var fixed = new RewardValuation.Grant(item, 5, true);
        var out = RiskReward.apply(List.of(rolled, fixed), 50);
        assertEquals(30, out.get(0).amount());
        assertEquals(5, out.get(1).amount());
        var same = List.of(rolled);
        assertSame(same, RiskReward.apply(same, 0));
    }
}
