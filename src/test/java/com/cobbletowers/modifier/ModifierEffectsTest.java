package com.cobbletowers.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.definition.ModifierDefinition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Offsets add, percentages compound, and nothing here decides what a level may be. */
class ModifierEffectsTest {

    @Test
    @DisplayName("nothing held is nothing changed")
    void neutral() {
        assertEquals(ModifierEffects.NONE, ModifierEffects.of(List.of()));
        assertEquals(0, ModifierEffects.NONE.levelOffset());
        assertEquals(100, ModifierEffects.NONE.bossHealthPercent());
        assertEquals(100, ModifierEffects.NONE.rewardPercent());
    }

    @Test
    @DisplayName("offsets add up")
    void offsetsAdd() {
        ModifierDefinition three = TestRuns.modifier("three", "enemy", "\"level_offset\":3");
        ModifierDefinition four = TestRuns.modifier("four", "enemy", "\"level_offset\":4");
        assertEquals(7, ModifierEffects.of(List.of(three, four)).levelOffset());
    }

    @Test
    @DisplayName("percentages compound rather than add")
    void percentagesCompound() {
        ModifierDefinition half = TestRuns.modifier("half", "reward", "\"reward_percent\":150");
        // Adding would give 200%; compounding gives 225%, which is what "half as much again, twice"
        // actually means -- and what each card claims on its own.
        assertEquals(225, ModifierEffects.of(List.of(half, half)).rewardPercent());
    }

    @Test
    @DisplayName("a boss pool can be reduced but never to nothing")
    void bossHealthFloor() {
        ModifierDefinition frail = TestRuns.modifier("frail", "enemy", "\"boss_health_percent\":1");
        ModifierEffects effects = ModifierEffects.of(List.of(frail, frail, frail, frail));
        assertTrue(effects.bossHealthPercent() >= 1, "a boss with a zero pool is a corpse, not a boss");
        assertTrue(effects.applyBossHealth(10_000L) >= 1L);
    }

    @Test
    @DisplayName("the boss pool scales by the percentage")
    void bossHealthScales() {
        ModifierDefinition tough = TestRuns.modifier("tough", "enemy", "\"boss_health_percent\":150");
        assertEquals(15_000L, ModifierEffects.of(List.of(tough)).applyBossHealth(10_000L));
    }

    @Test
    @DisplayName("extra opponents add up across copies")
    void extraOpponentsAdd() {
        ModifierDefinition crowd = TestRuns.modifier("crowd", "encounter", "\"extra_opponents\":1",
                "\"stack_limit\":3");
        assertEquals(3, ModifierEffects.of(List.of(crowd, crowd, crowd)).extraOpponents());
    }

    @Test
    @DisplayName("a modifier whose effect does not match its type is refused at load")
    void typeMustMatchEffect() {
        // The mistake content makes: copy a modifier, change its type, forget the payload. Without
        // this the file loads, the card drafts, and it does nothing that its type is applied by.
        assertThrows(IllegalArgumentException.class,
                () -> TestRuns.modifier("mislabelled", "reward", "\"level_offset\":3"));
    }

    @Test
    @DisplayName("a card nothing can apply yet is not offerable")
    void inertCardsAreNotOfferable() {
        assertTrue(TestRuns.modifier("real", "enemy", "\"level_offset\":1").effectiveNow());
        assertTrue(TestRuns.modifier("paid", "reward", "\"reward_percent\":120").effectiveNow());

        // Typed, validated and drafted-in-principle, but nothing in this build applies them: they
        // need a battle's rules changed, which is P8b through the CobbleRaids boundary.
        assertFalse(TestRuns.modifier("weather", "field", "\"weather\":\"raindance\"").effectiveNow());
        assertFalse(TestRuns.modifier("no_items", "player_constraint", "\"allow_items\":false").effectiveNow());
        // And the case a type-based check would get wrong: an ENEMY modifier whose only effect is a
        // boss pool the tower is never told the baseline of.
        assertFalse(TestRuns.modifier("frail_boss", "enemy", "\"boss_health_percent\":80").effectiveNow());
    }
}
