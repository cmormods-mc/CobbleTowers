package com.cobbletowers.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.RunModifierState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The lock-in cadence, the accumulation maths, and what a run is offered. */
class DraftServiceTest {

    private static final UUID RUN = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static RunModifierState holding(int challenges, int lockedIn) {
        RunModifierState state = RunModifierState.EMPTY;
        for (int i = 0; i < challenges; i++) state = state.accumulating(TestRuns.id("m" + i));
        for (int i = 0; i < lockedIn; i++) state = state.lockingIn(TestRuns.id("m" + i));
        return state;
    }

    @Test
    @DisplayName("a lock-in comes due on every fifth challenge and not before")
    void lockInCadence() {
        for (int challenges = 0; challenges < 5; challenges++) {
            assertFalse(DraftService.lockInDue(holding(challenges, 0)),
                    challenges + " challenges is not yet a lock-in");
        }
        assertTrue(DraftService.lockInDue(holding(5, 0)), "the fifth challenge owes a lock-in");
    }

    @Test
    @DisplayName("a lock-in already taken is not owed again until the next five")
    void lockInNotRepeated() {
        assertFalse(DraftService.lockInDue(holding(5, 1)), "that one has been had");
        assertFalse(DraftService.lockInDue(holding(9, 1)), "still the same five");
        assertTrue(DraftService.lockInDue(holding(10, 1)), "ten challenges owes a second");
    }

    @Test
    @DisplayName("locking in counts the modifier a second time")
    void lockInDoubles() {
        ModifierDefinition harder = TestRuns.modifier("harder", "enemy", "\"level_offset\":4");
        TowerContent content = TestRuns.contentWith(Map.of(harder.id(), harder));

        RunModifierState held = RunModifierState.EMPTY.accumulating(harder.id());
        assertEquals(4, DraftService.effects(content, held).levelOffset());

        RunModifierState locked = held.lockingIn(harder.id());
        assertEquals(8, DraftService.effects(content, locked).levelOffset(),
                "a locked-in modifier applies twice; that is what the Lock-In Draft is voting for");
    }

    @Test
    @DisplayName("a modifier the content no longer defines is skipped, not fatal")
    void unknownModifierIgnored() {
        // A datapack can be edited under an in-flight run. The digest makes that visible (TDS #40);
        // it must not make the run unreadable.
        RunModifierState held = RunModifierState.EMPTY.accumulating(TestRuns.id("deleted"));
        assertEquals(ModifierEffects.NONE, DraftService.effects(TestRuns.content(), held));
    }

    @Test
    @DisplayName("an ordinary draft offers what the run is eligible for")
    void ordinaryDraft() {
        ModifierDefinition base = TestRuns.modifier("base", "enemy", "\"level_offset\":1");
        ModifierDefinition needsBase = TestRuns.modifier("needs_base", "enemy", "\"level_offset\":2",
                "\"requires\":[\"cobbletowers:base\"]");
        TowerContent content = TestRuns.contentWith(
                Map.of(base.id(), base, needsBase.id(), needsBase));

        PersistedRun run = TestRuns.fresh(RUN);
        assertEquals(List.of(base.id()), DraftService.cardsFor(content, run, 1),
                "the prerequisite is not met, so only one card can be offered");
    }

    @Test
    @DisplayName("a lock-in draft offers what the run already holds, minus what is already locked")
    void lockInDraftOffersHeld() {
        Map<ResourceLocation, ModifierDefinition> loaded = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            ModifierDefinition modifier = TestRuns.modifier("m" + i, "enemy", "\"level_offset\":" + (i + 1));
            loaded.put(modifier.id(), modifier);
        }
        TowerContent content = TestRuns.contentWith(loaded);

        PersistedRun run = TestRuns.fresh(RUN).withModifiers(holding(5, 0), TestRuns.NOW);
        List<ResourceLocation> cards = DraftService.cardsFor(content, run, 5);

        assertEquals(3, cards.size());
        for (ResourceLocation card : cards) {
            assertTrue(run.modifiers().accumulated().contains(card),
                    card + " is not something this run holds, so it cannot be locked in");
        }
    }

    @Test
    @DisplayName("nothing eligible means no draft rather than an empty one")
    void noCardsNoDraft() {
        assertEquals(List.of(), DraftService.cardsFor(TestRuns.content(), TestRuns.fresh(RUN), 1));
    }

    @Test
    @DisplayName("only participants who can fight are entitled to vote")
    void votersAreFighters() {
        PersistedRun run = TestRuns.fresh(RUN);
        assertEquals(List.of(TestRuns.PLAYER), DraftService.voters(run));
    }

    @Test
    @DisplayName("the draft seed does not move once the cards are on the table")
    void seedIsFixedPerFloor() {
        PersistedRun run = TestRuns.fresh(RUN);
        assertEquals(DraftService.draftSeed(run, 4), DraftService.draftSeed(run, 4),
                "the tie-break is fixed before anybody votes, so voting order cannot steer it");
        assertTrue(DraftService.draftSeed(run, 4) != DraftService.draftSeed(run, 5));
    }

    @Test
    @DisplayName("an empty run carries no effects at all")
    void emptyRunIsNeutral() {
        assertEquals(ModifierEffects.NONE,
                DraftService.effects(TestRuns.content(), RunModifierState.EMPTY));
        assertEquals(Optional.empty(), RunModifierState.EMPTY.draft());
    }
}
