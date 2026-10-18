package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** The route (P50) as it is kept on disk: the lanes taken and the draft that carries lanes and kinds. */
class RouteStateTest {

    @Test
    void aRunThatHasTakenNoStepCanStartAnywhere() {
        assertEquals(-1, RunModifierState.EMPTY.lastLane());
        assertEquals(-1, RunModifierState.EMPTY.laneAt(3));
    }

    @Test
    void stepsAreRemembered() {
        RunModifierState state = RunModifierState.EMPTY.withRouteStep(1, 2).withRouteStep(2, 1).withRouteStep(4, 0);
        assertEquals(0, state.lastLane());
        assertEquals(2, state.laneAt(1));
        assertEquals(1, state.laneAt(2));
        assertEquals(-1, state.laneAt(3));
        assertEquals(List.of(1, 2, 2, 1, 4, 0), state.routeSteps());
    }

    @Test
    void stepsSurviveTheOtherChangesToAState() {
        RunModifierState state = RunModifierState.EMPTY.withRouteStep(1, 2)
                .accumulating(TestRuns.id("m1"), 2).withRelic(TestRuns.id("r1"), 2).lockingIn(TestRuns.id("m1"))
                .withRelic(TestRuns.id("r2"), 3).withoutRelic(TestRuns.id("r1"));
        assertEquals(2, state.lastLane());
        PersistedDraft draft = PersistedDraft.openingRoute(2, List.of(TestRuns.id("a"), TestRuns.id("b")), List.of(1, 2), List.of("BATTLE", "FORGE"));
        assertEquals(2, state.withDraft(draft).lastLane());
        assertEquals(2, state.withDraft(draft).withoutDraft().lastLane());
    }

    @Test
    void theRouteRoundTripsThroughTheTag() {
        RunModifierState state = RunModifierState.EMPTY.withRouteStep(1, 2).withRouteStep(2, 1);
        RunModifierState back = RunModifierState.fromTag(state.toTag());
        assertEquals(state.routeSteps(), back.routeSteps());
        assertEquals(1, back.lastLane());
    }

    @Test
    void aRunSavedBeforeRoutesHasNone() {
        CompoundTag tag = RunModifierState.EMPTY.toTag();
        assertFalse(tag.contains("route"));
        assertEquals(-1, RunModifierState.fromTag(tag).lastLane());
    }

    @Test
    void aRouteDraftKeepsItsLanesAndKindsThroughVotesResolutionAndTheTag() {
        PersistedDraft draft = PersistedDraft.openingRoute(3, List.of(TestRuns.id("a"), TestRuns.id("b")), List.of(0, 1), List.of("SHRINE", "BATTLE"));
        assertTrue(draft.route());
        java.util.UUID voter = java.util.UUID.fromString("22222222-2222-2222-2222-222222222222");
        PersistedDraft voted = draft.withVote(voter, 1);
        PersistedDraft resolved = voted.resolvedAs(1, false);
        for (PersistedDraft d : List.of(draft, voted, resolved)) {
            PersistedDraft back = PersistedDraft.fromTag(d.toTag());
            assertEquals(List.of(0, 1), back.lanes());
            assertEquals(List.of("SHRINE", "BATTLE"), back.kinds());
            assertTrue(back.route());
        }
        assertEquals(Optional.of(TestRuns.id("b")), resolved.chosenModifier());
    }

    @Test
    void aPlainDraftIsNotARouteDraftAndOldTagsStillLoad() {
        PersistedDraft plain = PersistedDraft.opening(2, false, List.of(TestRuns.id("a"), TestRuns.id("b")));
        assertFalse(plain.route());
        CompoundTag tag = plain.toTag();
        assertFalse(tag.contains("lanes"));
        assertFalse(PersistedDraft.fromTag(tag).route());
        // a half-written route draft (lanes without kinds) is read as a plain one rather than refused
        CompoundTag torn = PersistedDraft.openingRoute(2, List.of(TestRuns.id("a")), List.of(0), List.of("REST")).toTag();
        torn.remove("kinds");
        assertFalse(PersistedDraft.fromTag(torn).route());
    }

    @Test
    void aRouteDraftMustNameALaneAndAKindPerCard() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PersistedDraft.openingRoute(2, List.of(TestRuns.id("a"), TestRuns.id("b")), List.of(0), List.of("REST")));
    }
}
