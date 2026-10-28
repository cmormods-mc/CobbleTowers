package com.cobbletowers.halloween.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VaultMachineTest {

    private static final VaultManifest MANIFEST = VaultManifest.load();

    private static VaultMachine machine() {
        return MANIFEST.newMachine();
    }

    /** Runs a whole cycle: start, play, close the vault `openFor` ticks into OPEN, run until CLOSED; returns every frame. */
    private static List<VaultMachine.Frame> cycle(VaultMachine m, VaultMachine.Outcome outcome, int openFor) {
        List<VaultMachine.Frame> frames = new ArrayList<>();
        assertTrue(m.start(outcome));
        int open = 0;
        boolean closing = false;
        while (true) {
            VaultMachine.State before = m.state();
            frames.add(m.tick());
            if (before == VaultMachine.State.OPEN && ++open == openFor) assertTrue(m.close());
            if (before == VaultMachine.State.CLOSING) closing = true;
            if (closing && m.state() == VaultMachine.State.CLOSED) return frames;
            assertTrue(frames.size() < 5000, "cycle never ended");
        }
    }

    @Test
    void stateDurationsAreTheManifestOnes() {
        Map<VaultMachine.State, Integer> count = new EnumMap<>(VaultMachine.State.class);
        for (VaultMachine.Frame f : cycle(machine(), VaultMachine.Outcome.TREAT, 60)) count.merge(f.state(), 1, Integer::sum);
        assertEquals(10, count.get(VaultMachine.State.ANTICIPATING));   // 0.5 s
        assertEquals(5, count.get(VaultMachine.State.UNLOCKING));       // 0.25 s
        assertEquals(14, count.get(VaultMachine.State.OPENING));        // 0.7 s
        assertEquals(60, count.get(VaultMachine.State.OPEN));
        assertEquals(14, count.get(VaultMachine.State.CLOSING));        // 0.7 s
    }

    @Test
    void lidPeaksThreeDegreesOverAndSettlesAtTheFinalAngle() {
        double peak = 0, last = 0;
        for (VaultMachine.Frame f : cycle(machine(), VaultMachine.Outcome.TREAT, 40)) {
            peak = Math.max(peak, f.pose().lidDeg());
            if (f.state() == VaultMachine.State.OPEN && f.stateTick() == 0) last = f.pose().lidDeg();
        }
        double max = MANIFEST.animation().getAsJsonObject("coordinate_contract").getAsJsonObject("hinge").get("max_open_deg").getAsDouble();
        assertEquals(115.0, max, 1e-9);
        assertEquals(max + 3.0, peak, 1e-9);
        assertEquals(max, last, 1e-9);
    }

    @Test
    void transitionsAreGuarded() {
        VaultMachine m = machine();
        assertFalse(m.close(), "nothing to close while CLOSED");
        assertTrue(m.start(VaultMachine.Outcome.TRICK));
        assertFalse(m.start(VaultMachine.Outcome.TREAT), "no second start");
        assertFalse(m.close(), "cannot close before OPEN");
        assertEquals(VaultMachine.Outcome.TRICK, m.outcome());
    }

    @Test
    void resetWorksFromEveryStateAtEveryTick() {
        for (VaultMachine.State target : VaultMachine.State.values()) {
            for (int extra = 0; extra < 15; extra++) {
                VaultMachine m = machine();
                m.start(VaultMachine.Outcome.TREAT);
                while (m.state() != target) {
                    m.tick();
                    if (target == VaultMachine.State.CLOSED) break;
                    if (m.state() == VaultMachine.State.OPEN && target == VaultMachine.State.CLOSING) m.close();
                }
                for (int i = 0; i < extra; i++) m.tick();
                m.reset();
                assertEquals(VaultMachine.State.CLOSED, m.state());
                VaultMachine.Frame f = m.tick();
                assertEquals(0.0, f.pose().lidDeg(), 1e-12, target + "+" + extra);
                assertEquals(0.0, f.pose().plateUnits(), 1e-12);
                assertEquals(0.0, f.pose().rootXUnits(), 1e-12);
                assertEquals(null, m.outcome());
            }
        }
    }

    @Test
    void trickAndTreatAreIdenticalUntilTheLidIsOpen() {
        List<VaultMachine.Frame> treat = cycle(machine(), VaultMachine.Outcome.TREAT, 30), trick = cycle(machine(), VaultMachine.Outcome.TRICK, 30);
        int compared = 0;
        for (int i = 0; i < treat.size(); i++) {
            VaultMachine.Frame a = treat.get(i), b = trick.get(i);
            if (a.state() == VaultMachine.State.OPEN) break;
            assertEquals(a.state(), b.state());
            assertEquals(a.pose(), b.pose());
            assertEquals(a.events(), b.events(), "events before OPEN must not depend on the outcome");
            compared++;
        }
        assertEquals(10 + 5 + 14, compared);
        VaultMachine.Frame firstTreat = treat.get(compared), firstTrick = trick.get(compared);
        assertTrue(firstTreat.events().stream().anyMatch(e -> e.type().equals("reward_trigger")));
        assertTrue(firstTrick.events().stream().anyMatch(e -> e.type().equals("encounter_trigger")));
        assertFalse(firstTreat.events().stream().anyMatch(e -> e.type().equals("encounter_trigger")));
    }

    @Test
    void hundredAutomatedCyclesEndInTheIdenticalClosedTransforms() {
        VaultMachine m = machine();
        List<VaultTransforms.Xform> first = null;
        for (int cycle = 0; cycle < 120; cycle++) {
            VaultMachine.Outcome outcome = cycle % 2 == 0 ? VaultMachine.Outcome.TREAT : VaultMachine.Outcome.TRICK;
            List<VaultTransforms.Xform> startPose = closed(m);
            assertEquals(VaultMachine.State.CLOSED, m.state());
            cycle(m, outcome, outcome == VaultMachine.Outcome.TRICK ? 20 : 60);
            assertEquals(VaultMachine.State.CLOSED, m.state());
            List<VaultTransforms.Xform> endPose = closed(m);
            if (first == null) first = startPose;
            for (int i = 0; i < first.size(); i++) {
                assertTrue(java.util.Arrays.equals(first.get(i).t(), startPose.get(i).t()) && java.util.Arrays.equals(first.get(i).q(), startPose.get(i).q()), "cycle " + cycle + " start");
                assertTrue(java.util.Arrays.equals(first.get(i).t(), endPose.get(i).t()) && java.util.Arrays.equals(first.get(i).q(), endPose.get(i).q()), "cycle " + cycle + " end");
            }
            // closed idle restarts from its first tick after every cycle, so the next cycle begins from the same pose
            assertEquals(0, m.stateTick());
        }
    }

    /** The closed-pose transforms of the four persistent pieces, from the pose at the first tick of the CLOSED state. */
    private static List<VaultTransforms.Xform> closed(VaultMachine m) {
        VaultPose pose = MANIFEST.timeline().sample("closed_idle", 0);
        List<VaultTransforms.Xform> out = new ArrayList<>();
        for (VaultPiece p : VaultPiece.values()) if (p.persistent) out.add(VaultTransforms.piece(p, pose, VaultFacing.NORTH));
        return out;
    }
}
