package com.cobbletowers.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.definition.ModifierDefinition;
import java.util.List;
import java.util.Optional;
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
    @DisplayName("every type is offerable now that every field has somewhere to go")
    void everyTypeIsOfferable() {
        // P8a could not offer these: a battle's rules were not the tower's to change, and a boss
        // pool derived inside CobbleRaids was not the tower's to scale. P8b gave all three a road
        // across the encounter boundary, so all three are real cards.
        assertTrue(TestRuns.modifier("real", "enemy", "\"level_offset\":1").effectiveNow());
        assertTrue(TestRuns.modifier("paid", "reward", "\"reward_percent\":120").effectiveNow());
        assertTrue(TestRuns.modifier("weather", "field", "\"weather\":\"raindance\"").effectiveNow());
        assertTrue(TestRuns.modifier("no_items", "player_constraint", "\"allow_items\":false").effectiveNow());
        assertTrue(TestRuns.modifier("tough_boss", "enemy", "\"boss_health_percent\":130").effectiveNow());
    }

    @Test
    @DisplayName("a card that would do nothing cannot be defined at all")
    void inertCardsCannotExist() {
        // The guarantee that replaced the P8a filter, and it is the stronger one: rather than
        // loading a do-nothing modifier and declining to offer it, the definition is refused. Every
        // type now has at least one field that reaches something, so an effect that changes nothing
        // matches no type and cannot get past the constructor.
        for (String type : new String[] {"enemy", "encounter", "player_constraint", "field", "reward"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> TestRuns.modifier("empty_" + type, type, ""),
                    type + " accepted an effect that changes nothing");
        }
    }

    @Test
    @DisplayName("a withdrawn permission stays withdrawn")
    void permissionsOnlyNarrow() {
        ModifierDefinition noSwitch = TestRuns.modifier("no_switch", "player_constraint",
                "\"allow_switching\":false");
        ModifierDefinition ordinary = TestRuns.modifier("ordinary", "enemy", "\"level_offset\":1");

        // Order must not matter: an ordinary modifier drafted after a restriction cannot hand the
        // permission back, because its own effect says "switching allowed" simply by not mentioning it.
        assertFalse(ModifierEffects.of(List.of(noSwitch, ordinary)).switchingAllowed());
        assertFalse(ModifierEffects.of(List.of(ordinary, noSwitch)).switchingAllowed());
    }

    @Test
    @DisplayName("banned moves are pooled across every modifier holding them")
    void bannedMovesUnion() {
        ModifierDefinition one = TestRuns.modifier("one", "player_constraint", "\"banned_moves\":[\"protect\"]");
        ModifierDefinition two = TestRuns.modifier("two", "player_constraint",
                "\"banned_moves\":[\"recover\",\"protect\"]");

        assertEquals(List.of("protect", "recover"), ModifierEffects.of(List.of(one, two)).bannedMoves(),
                "pooled and deduplicated, in the order they were drafted");
    }

    @Test
    @DisplayName("the last field condition drafted is the one that applies")
    void lastFieldWins() {
        ModifierDefinition rain = TestRuns.modifier("rain", "field", "\"weather\":\"raindance\"");
        ModifierDefinition sun = TestRuns.modifier("sun", "field", "\"weather\":\"sunnyday\"");

        // A field is one condition, so somebody has to lose. Taking the last means the card the
        // party just voted for is the one they get; taking the first would silently ignore it.
        assertEquals(Optional.of("sunnyday"), ModifierEffects.of(List.of(rain, sun)).weather());
        assertEquals(Optional.of("raindance"), ModifierEffects.of(List.of(sun, rain)).weather());
    }

    @Test
    @DisplayName("nothing drafted changes no battle rules")
    void neutralChangesNoRules() {
        assertFalse(ModifierEffects.NONE.changesBattleRules());
        assertTrue(ModifierEffects.of(List.of(
                TestRuns.modifier("tough", "enemy", "\"boss_health_percent\":130"))).changesBattleRules());
        assertFalse(ModifierEffects.of(List.of(
                TestRuns.modifier("plain", "enemy", "\"level_offset\":2"))).changesBattleRules(),
                "a level offset is not a battle rule; it is applied when the opponent is drawn");
    }
}
