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
    void lineTagsRoundTrip() {
        assertEquals(Optional.of(2), PedestalRules.lineOf(PedestalRules.lineTag(2)));
        assertTrue(PedestalRules.lineOf("cobbletowers_pedestal_line_-3").isEmpty());
        assertTrue(PedestalRules.lineOf("nope").isEmpty());
    }

    @Test
    void theTextStacksUpwardWithoutOverlap() {
        PedestalRules.Stack stack = PedestalRules.stack(PedestalRules.BODY_LINES);
        assertEquals(PedestalRules.TEXT_BASE, stack.bodyY(), 1e-9);
        assertTrue(stack.bigY() >= stack.bodyY() + PedestalRules.textHeight(PedestalRules.BODY_LINES, PedestalRules.BODY_SCALE));
        assertTrue(stack.nameY() >= stack.bigY() + PedestalRules.textHeight(1, PedestalRules.BIG_SCALE));
        assertTrue(stack.top() <= PedestalRules.HITBOX_HEIGHT, "the click box covers the whole stack: " + stack.top());
    }

    @Test
    void theGemRotationIsAUnitQuaternionAndTurnsAboutItsCentre() {
        for (double spin : new double[] {0, Math.PI / 2, Math.PI, 2.3}) {
            double[] q = PedestalRules.gemRotation(spin);
            assertEquals(1.0, Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]), 1e-9);
            // the cube's centre, rotated and shifted by the offset, lands back on the centre of the unrotated cube
            double s = 0.42;
            double[] c = PedestalRules.rotate(q, s / 2, s / 2, s / 2);
            double[] offset = PedestalRules.centreOffset(q, s);
            assertEquals(0.0, c[0] + offset[0], 1e-9);
            assertEquals(0.0, c[1] + offset[1], 1e-9);
            assertEquals(0.0, c[2] + offset[2], 1e-9);
        }
    }

    @Test
    void theNearerOfTwoOverlappingPedestalsWins() {
        List<PedestalRules.Slot> slots = List.of(new PedestalRules.Slot(0, 0, 0), new PedestalRules.Slot(-2, 0, 0));
        assertEquals(Optional.of(0), PedestalRules.gazedAt(4, 1, 0, -1, 0, 0, slots));
    }
}
