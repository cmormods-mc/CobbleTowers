package com.cobbletowers.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class TowerModifierProgressTest {

    @Test
    void fifthAcceptedChallengeRequiresChoiceFromPreviousFive() {
        TowerModifierProgress progress = new TowerModifierProgress();
        ResourceLocation a = id("a");
        ResourceLocation b = id("b");
        ResourceLocation c = id("c");
        ResourceLocation d = id("d");
        ResourceLocation e = id("e");

        for (ResourceLocation modifier : List.of(a, b, c, d, e)) {
            progress.acceptTemporary(modifier);
            progress.consumeTemporary();
        }

        assertEquals(5, progress.acceptedChallengeCount());
        assertTrue(progress.promotionPending());
        assertEquals(List.of(a, b, c, d, e), progress.promotionChoices());
        assertThrows(IllegalArgumentException.class, () -> progress.promote(id("not_eligible")));

        progress.promote(c);
        assertFalse(progress.promotionPending());
        assertEquals(Map.of(c, 1), progress.permanentTiers());
    }

    @Test
    void repeatedPromotionUpgradesTierInsteadOfAddingDuplicate() {
        TowerModifierProgress progress = new TowerModifierProgress();
        ResourceLocation fortified = id("fortified");

        for (int cycle = 0; cycle < 2; cycle++) {
            for (int i = 0; i < 5; i++) {
                progress.acceptTemporary(fortified);
                progress.consumeTemporary();
            }
            progress.promote(fortified);
        }

        assertEquals(10, progress.acceptedChallengeCount());
        assertEquals(1, progress.permanentTiers().size());
        assertEquals(2, progress.permanentTiers().get(fortified));
    }

    @Test
    void cannotAcceptAnotherChallengeWhilePromotionIsPending() {
        TowerModifierProgress progress = new TowerModifierProgress();
        for (int i = 0; i < 5; i++) {
            progress.acceptTemporary(id("m" + i));
            progress.consumeTemporary();
        }

        assertThrows(IllegalStateException.class, () -> progress.acceptTemporary(id("sixth")));
    }

    @Test
    void temporaryModifierIsConsumedExactlyOnce() {
        TowerModifierProgress progress = new TowerModifierProgress();
        ResourceLocation modifier = id("fragile_power");
        progress.acceptTemporary(modifier);

        assertEquals(modifier, progress.consumeTemporary());
        assertNull(progress.consumeTemporary());
    }

    @Test
    void restorePreservesHiddenRecentWindowBeforePromotionThreshold() {
        ResourceLocation a = id("a");
        ResourceLocation b = id("b");
        TowerModifierProgress restored = TowerModifierProgress.restore(
                2,
                false,
                null,
                List.of(a, b),
                Map.of()
        );

        assertTrue(restored.promotionChoices().isEmpty());
        assertEquals(List.of(a, b), restored.promotionChoicesForPersistence());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }
}
