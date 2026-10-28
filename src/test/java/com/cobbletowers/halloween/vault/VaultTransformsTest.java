package com.cobbletowers.halloween.vault;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class VaultTransformsTest {

    private static final double EPS = 1e-9;
    private static final double[] HINGE_MODEL = {8, 13.5, 19};

    private static VaultPose lid(double deg) {
        return new VaultPose(deg, 0, 0);
    }

    @Test
    void atRestEveryPersistentPieceHasTheSameIdentityTransformInVaultSpace() {
        for (VaultFacing f : VaultFacing.values()) {
            VaultTransforms.Xform body = VaultTransforms.piece(VaultPiece.BODY, VaultPose.REST, f);
            for (VaultPiece p : new VaultPiece[] {VaultPiece.LID, VaultPiece.FACE_PLATE, VaultPiece.CANDY_FILL}) {
                VaultTransforms.Xform x = VaultTransforms.piece(p, VaultPose.REST, f);
                assertArrayEquals(body.t(), x.t(), EPS, p + " " + f);
                assertArrayEquals(body.q(), x.q(), EPS, p + " " + f);
            }
        }
    }

    @Test
    void theModelFrontPointsWhereTheFacingSays() {
        double[][] expect = {{0, 0, -1}, {-1, 0, 0}, {0, 0, 1}, {1, 0, 0}};   // NORTH, WEST, SOUTH, EAST in enum order
        for (VaultFacing f : VaultFacing.values()) {
            // the client draws T + q (F v): flip the model's front vector, then apply the sent rotation
            double[] v = VaultMath.rotate(VaultTransforms.piece(VaultPiece.BODY, VaultPose.REST, f).q(), VaultMath.flip(new double[] {0, 0, -1}));
            assertArrayEquals(expect[f.ordinal()], v, EPS, f.name());
        }
    }

    @Test
    void theRestRotationSentIsTheHalfTurnThatCancelsTheClientsOwn() {
        assertArrayEquals(new double[] {0, 1, 0, 0}, VaultTransforms.piece(VaultPiece.BODY, VaultPose.REST, VaultFacing.NORTH).q(), EPS);
        // SOUTH: the facing's half turn and the client-cancelling one make a full turn: the identity rotation (q and -q are the same rotation)
        double[] south = VaultTransforms.piece(VaultPiece.BODY, VaultPose.REST, VaultFacing.SOUTH).q();
        assertEquals(1.0, Math.abs(south[3]), EPS);
    }

    @Test
    void hingeAxisPointsNeverMoveForAnyAngleInAnyFacing() {
        for (VaultFacing f : VaultFacing.values()) {
            for (double deg : new double[] {1.5, 30, 90, 115, 118}) {
                for (double x : new double[] {0, 3, 8, 13, 16}) {
                    double[] p = {x, HINGE_MODEL[1], HINGE_MODEL[2]};
                    assertArrayEquals(VaultTransforms.worldPoint(VaultPiece.LID, VaultPose.REST, f, p), VaultTransforms.worldPoint(VaultPiece.LID, lid(deg), f, p), EPS);
                }
            }
        }
    }

    @Test
    void theHingeConstantMatchesTheManifest() {
        var h = VaultManifest.load().animation().getAsJsonObject("coordinate_contract").getAsJsonObject("hinge").getAsJsonArray("point_root_local_blocks");
        for (int i = 0; i < 3; i++) assertEquals(h.get(i).getAsDouble(), VaultTransforms.HINGE[i], 1e-6);
    }

    @Test
    void positiveAngleLiftsTheFrontAndSwingsItBack() {
        double[] front = {8, 13.5, 0};
        double[] a = VaultTransforms.worldPoint(VaultPiece.LID, VaultPose.REST, VaultFacing.NORTH, front);
        double[] b = VaultTransforms.worldPoint(VaultPiece.LID, lid(30), VaultFacing.NORTH, front);
        assertTrue(b[1] > a[1] && b[2] > a[2]);
    }

    @Test
    void lidMatchesTheIndependentMatrixFormula() {
        // root * translate(h) * Rx * translate(-h) applied to a lid point, written out as matrices
        double[] pt = {5, 17, 4};
        for (double deg : new double[] {10, 45, 100, 118}) {
            double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
            double dy = pt[1] - HINGE_MODEL[1], dz = pt[2] - HINGE_MODEL[2];
            double[] want = {pt[0], HINGE_MODEL[1] + dy * c - dz * s, HINGE_MODEL[2] + dy * s + dz * c};
            double[] got = VaultTransforms.worldPoint(VaultPiece.LID, lid(deg), VaultFacing.NORTH, pt);
            assertArrayEquals(want, new double[] {got[0] * 16 + 8, got[1] * 16, got[2] * 16 + 8}, 1e-9, "deg " + deg);
        }
    }

    @Test
    void facingRotatesTheWholeAssemblyAboutTheRoot() {
        double[] p = {5, 12, 4};
        VaultPose pose = lid(70);
        double[] north = VaultTransforms.worldPoint(VaultPiece.LID, pose, VaultFacing.NORTH, p);
        for (VaultFacing f : VaultFacing.values()) {
            double[] want = VaultMath.rotate(VaultMath.qAxis('y', f.yawDeg), north);
            assertArrayEquals(want, VaultTransforms.worldPoint(VaultPiece.LID, pose, f, p), EPS, f.name());
        }
    }

    @Test
    void javaMatchesThePythonReferenceSamplesTickForTick() throws Exception {
        JsonObject gold;
        try (var in = new InputStreamReader(VaultTransformsTest.class.getResourceAsStream("/vault/animation_golden.json"), StandardCharsets.UTF_8)) {
            gold = JsonParser.parseReader(in).getAsJsonObject();
        }
        VaultTimeline timeline = VaultManifest.load().timeline();
        int samples = 0;
        for (var seq : gold.getAsJsonObject("sequences").entrySet()) {
            JsonArray frames = seq.getValue().getAsJsonArray();
            for (int tick = 0; tick < frames.size(); tick++) {
                JsonObject want = frames.get(tick).getAsJsonObject();
                VaultPose got = timeline.sample(seq.getKey(), tick);
                assertEquals(want.get("lid.rot_x_deg").getAsDouble(), got.lidDeg(), 2e-6, seq.getKey() + "@" + tick);
                assertEquals(want.get("face_plate.press_units").getAsDouble(), got.plateUnits(), 2e-6, seq.getKey() + "@" + tick);
                assertEquals(want.get("root.x_units").getAsDouble(), got.rootXUnits(), 2e-6, seq.getKey() + "@" + tick);
                samples++;
            }
        }
        for (JsonElement e : gold.getAsJsonArray("transforms")) {
            JsonObject o = e.getAsJsonObject();
            VaultFacing facing = VaultFacing.parse(o.get("facing").getAsString());
            VaultPiece piece = VaultPiece.valueOf(o.get("piece").getAsString().toUpperCase(java.util.Locale.ROOT));
            VaultPose pose = new VaultPose(o.get("lid").getAsDouble(), o.get("press").getAsDouble(), o.get("root_x").getAsDouble());
            VaultTransforms.Xform x = VaultTransforms.piece(piece, pose, facing);
            for (int i = 0; i < 3; i++) assertEquals(o.getAsJsonArray("t").get(i).getAsDouble(), x.t()[i], 2e-6, "t " + facing + piece + pose);
            // a quaternion and its negative are the same rotation: compare up to sign
            double dot = 0;
            for (int i = 0; i < 4; i++) dot += o.getAsJsonArray("q").get(i).getAsDouble() * x.q()[i];
            double sign = dot < 0 ? -1 : 1;
            for (int i = 0; i < 4; i++) assertEquals(o.getAsJsonArray("q").get(i).getAsDouble(), sign * x.q()[i], 2e-6, "q " + facing + piece + pose);
            samples++;
        }
        assertTrue(samples > 200, "only " + samples + " golden samples compared");
    }

    @Test
    void revealCandyStartsInsideThePileAndRisesAboveTheOpenLid() {
        VaultReveal reveal = new VaultReveal(VaultManifest.load().animation());
        VaultReveal.State first = reveal.state(0, 0);
        assertTrue(first.blocks()[1] * 16 < 13.5 && Math.abs(first.blocks()[0]) < 5.0 / 16 && Math.abs(first.blocks()[2]) < 5.0 / 16);
        assertTrue(reveal.state(0, 40).blocks()[1] * 16 > 20);
        assertEquals(null, reveal.state(2, 2), "the third candy waits for its stagger");
        assertEquals(0.3, first.scale(), 1e-9);
    }
}
