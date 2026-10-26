package com.cobbletowers.intermission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PedestalRulesTest {

    @Test
    void tagsRoundTrip() {
        UUID run = UUID.randomUUID();
        assertEquals(Optional.of(run), PedestalRules.runOf(PedestalRules.runTag(run)));
        assertEquals(Optional.of(2), PedestalRules.cardOf(PedestalRules.cardTag(2)));
    }

    @Test
    void foreignOrMalformedTagsNameNothing() {
        assertTrue(PedestalRules.runOf("cobbletowers_pedestal").isEmpty());
        assertTrue(PedestalRules.runOf("cobbletowers_pedestal_run:nope").isEmpty());
        assertTrue(PedestalRules.cardOf("cobbletowers_pedestal_card_x").isEmpty());
        assertTrue(PedestalRules.cardOf("cobbletowers_pedestal_card_-1").isEmpty());
    }

    @Test
    void threePedestalsStandInARowTowardTheTarget() {
        // Vendor at x=54.5, looking west: the row is at x=50.5, spread along z.
        List<PedestalRules.Slot> slots = PedestalRules.layout(54.5, 42.5, 40.5, 42.5, 42, 3);
        assertEquals(3, slots.size());
        for (PedestalRules.Slot slot : slots) {
            assertEquals(50.5, slot.x(), 1e-9);
            assertEquals(42, slot.y(), 1e-9);
        }
        assertEquals(42.5, slots.get(1).z(), 1e-9);
        assertEquals(PedestalRules.SPACING, Math.abs(slots.get(2).z() - slots.get(1).z()), 1e-9);
        assertEquals(slots.get(1).z() - slots.get(0).z(), slots.get(2).z() - slots.get(1).z(), 1e-9);
    }

    @Test
    void aSinglePedestalSitsOnTheLine() {
        List<PedestalRules.Slot> slots = PedestalRules.layout(0, 0, 0, 10, 5, 1);
        assertEquals(0, slots.get(0).x(), 1e-9);
        assertEquals(PedestalRules.ROW_DISTANCE, slots.get(0).z(), 1e-9);
    }

    @Test
    void aZeroLengthDirectionStillLaysOut() {
        assertEquals(2, PedestalRules.layout(1, 1, 1, 1, 0, 2).size());
    }

    @Test
    void gazeFindsThePedestalInFront() {
        List<PedestalRules.Slot> slots = PedestalRules.layout(54.5, 42.5, 40.5, 42.5, 42, 3);
        // Standing east of the middle pedestal, eyes at 43.6, looking west level.
        assertEquals(Optional.of(1), PedestalRules.gazedAt(53.5, 43.6, 42.5, -1, 0, 0, slots));
    }

    @Test
    void gazeAimedBetweenPedestalsFindsNothing() {
        List<PedestalRules.Slot> slots = PedestalRules.layout(54.5, 42.5, 40.5, 42.5, 42, 3);
        double z = (slots.get(0).z() + slots.get(1).z()) / 2;
        assertTrue(PedestalRules.gazedAt(53.5, 43.6, z, -1, 0, 0, slots).isEmpty());
    }

    @Test
    void gazePastTheRangeFindsNothing() {
        List<PedestalRules.Slot> slots = List.of(new PedestalRules.Slot(0, 0, 0));
        assertTrue(PedestalRules.gazedAt(PedestalRules.GAZE_RANGE + 5, 1, 0, -1, 0, 0, slots).isEmpty());
        assertEquals(Optional.of(0), PedestalRules.gazedAt(PedestalRules.GAZE_RANGE - 1, 1, 0, -1, 0, 0, slots));
    }

    @Test
    void gazeLookingAwayFindsNothing() {
        List<PedestalRules.Slot> slots = List.of(new PedestalRules.Slot(0, 0, 0));
        assertTrue(PedestalRules.gazedAt(3, 1, 0, 1, 0, 0, slots).isEmpty());
    }

    @Test
    void theNearerOfTwoOverlappingPedestalsWins() {
        List<PedestalRules.Slot> slots = List.of(new PedestalRules.Slot(0, 0, 0), new PedestalRules.Slot(-2, 0, 0));
        assertEquals(Optional.of(0), PedestalRules.gazedAt(4, 1, 0, -1, 0, 0, slots));
    }
}
