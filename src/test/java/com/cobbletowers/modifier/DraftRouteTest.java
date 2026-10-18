package com.cobbletowers.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.persistence.PersistedDraft;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.RunModifierState;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The step of the route a draft becomes (P50): which cards ride on which lanes, and what each stop will do. */
class DraftRouteTest {

    private static final UUID RUN = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final List<ResourceLocation> CARDS = List.of(TestRuns.id("a"), TestRuns.id("b"), TestRuns.id("c"));

    private static PersistedRun run(long seed, RunModifierState state) {
        PersistedRun base = TestRuns.fresh(RUN);
        return new PersistedRun(base.runId(), base.schemaVersion(), base.towerId(), base.towerRevision(), base.towerDigest(), base.rulesetRevision(),
                base.structureRevision(), seed, base.floorIndex(), base.state(), base.participants(), base.lastCheckpoint(), base.committedTransactions(),
                base.updatedAt(), base.cell(), base.ledger(), state, base.lastBankedFloor(), base.vendorPurchases());
    }

    private static PersistedDraft step(long seed, int lastLane, List<ResourceLocation> cards) {
        RunModifierState state = lastLane < 0 ? RunModifierState.EMPTY : RunModifierState.EMPTY.withRouteStep(0, lastLane);
        return DraftService.routeDraft(TestRuns.content(), run(seed, state), cards, id -> Long.MAX_VALUE);
    }

    @Test
    void theFirstStepCanGoAnywhereAndEachCardRidesOnItsOwnLane() {
        PersistedDraft draft = step(42, -1, CARDS);
        assertEquals(CARDS, draft.cards());
        assertEquals(List.of(0, 1, 2), draft.lanes());
        assertEquals(3, draft.kinds().size());
        assertTrue(draft.route());
    }

    @Test
    void aPartyOnTheEdgeCanOnlyStepToTheLanesNextToIt() {
        PersistedDraft top = step(42, 0, CARDS);
        assertEquals(List.of(0, 1), top.lanes());
        assertEquals(List.of(CARDS.get(0), CARDS.get(1)), top.cards());
        PersistedDraft bottom = step(42, 2, CARDS);
        assertEquals(List.of(1, 2), bottom.lanes());
        assertEquals(List.of(CARDS.get(1), CARDS.get(2)), bottom.cards());
        assertEquals(List.of(0, 1, 2), step(42, 1, CARDS).lanes());
    }

    @Test
    void aStopThatCouldDoNothingIsAPlainBattle() {
        // the test content has no relics and nobody holds one, so every relic room would be empty; only Rest can run
        boolean rest = false;
        for (long seed = 1; seed <= 300; seed++) {
            for (String kind : step(seed, -1, CARDS).kinds()) {
                assertTrue(kind.equals("BATTLE") || kind.equals("REST"), kind);
                rest |= kind.equals("REST");
            }
        }
        assertTrue(rest, "Rest stops still appear");
    }

    @Test
    void aSmallPoolStillOffersAStep() {
        PersistedDraft one = step(42, 2, List.of(CARDS.get(0)));
        assertEquals(List.of(0), one.lanes(), "lane 0 is not next to lane 2, but a step with no card would stall the run");
        assertEquals(List.of(CARDS.get(0)), one.cards());
        PersistedDraft two = step(42, -1, List.of(CARDS.get(0), CARDS.get(1)));
        assertEquals(List.of(0, 1), two.lanes());
    }

    @Test
    void theSameRunAndFloorGiveTheSameStep() {
        assertEquals(step(1234, 1, CARDS), step(1234, 1, CARDS));
    }

    @Test
    void routesCanBeSwitchedOffAndAreOffWithoutRooms() {
        String route = System.getProperty("cobbletowers.route"), events = System.getProperty("cobbletowers.events");
        try {
            System.clearProperty("cobbletowers.route");
            System.clearProperty("cobbletowers.events");
            assertTrue(DraftService.routeEnabled());
            System.setProperty("cobbletowers.route", "off");
            assertTrue(!DraftService.routeEnabled());
            System.clearProperty("cobbletowers.route");
            System.setProperty("cobbletowers.events", "off");
            assertTrue(!DraftService.routeEnabled(), "a test that switches rooms off wants the plain draft");
        } finally {
            if (route == null) System.clearProperty("cobbletowers.route"); else System.setProperty("cobbletowers.route", route);
            if (events == null) System.clearProperty("cobbletowers.events"); else System.setProperty("cobbletowers.events", events);
        }
    }
}
