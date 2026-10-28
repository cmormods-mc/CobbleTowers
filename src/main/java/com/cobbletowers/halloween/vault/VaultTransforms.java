package com.cobbletowers.halloween.vault;

import static com.cobbletowers.halloween.vault.VaultMath.add;
import static com.cobbletowers.halloween.vault.VaultMath.qAxis;
import static com.cobbletowers.halloween.vault.VaultMath.qMul;
import static com.cobbletowers.halloween.vault.VaultMath.rotate;
import static com.cobbletowers.halloween.vault.VaultMath.sub;

/**
 * The coordinate contract of the vault, in code (see the {@code coordinate_contract} block of animation.json).
 *
 * <p>Every display entity is spawned at the vault ROOT (the floor centre) with yaw 0; a piece's item model is drawn centred on the entity,
 * so model point (8, 8, 8) sits at root + (0, 0.5, 0) blocks, which is every persistent piece's rest translation. The lid turns about the
 * hinge H (root-local blocks): {@code Tc = H + R(c - H)}, i.e. root x translate(H) x R x translate(-H) x rest, with no second offset anywhere.
 * Facing {@code Rnet} (a rotation about +y, net: north 0, west 90, south 180, east -90) is applied last to the translation and the rotation,
 * never as entity yaw.
 *
 * <p>Minecraft's item display renderer then turns the item MODEL a half circle about y inside its own frame (the client draws
 * {@code T + q (F v)}, F = rotY(180); measured in a real 1.21.1 client: at identity the model's north face looks south). So what is SENT is
 * {@code T = Rnet Tc} and {@code q = Rnet R F}; the client's own F cancels the one in q and the pose lands as intended.
 */
public final class VaultTransforms {

    /** Rest translation of a persistent piece (blocks, root-local). */
    /** rotY(180) as a quaternion: the half turn the client gives the model, cancelled by sending it again. */
    public static final double[] FLIP_Q = {0, 1, 0, 0};
    public static final double[] CENTRE = {0, 0.5, 0};
    /** A point on the hinge axis, root-local blocks: model (8, 12.5, 19.6) -> ((8-8)/16, 12.5/16, (19.6-8)/16). The manifest carries the same point (a test checks). */
    public static final double[] HINGE = {0, 12.5 / 16, 11.6 / 16};

    private VaultTransforms() {}

    /** A display transformation: translation in blocks and left rotation (xyzw); scale is always 1. */
    public record Xform(double[] t, double[] q) {}

    public static Xform piece(VaultPiece piece, VaultPose pose, VaultFacing facing) {
        double[] t, q = {0, 0, 0, 1};
        switch (piece) {
            case LID -> {
                q = qAxis('x', pose.lidDeg());
                t = add(HINGE, rotate(q, sub(CENTRE, HINGE)));
            }
            case FACE_PLATE -> t = add(CENTRE, new double[] {0, 0, pose.plateUnits() / 16});
            case BODY, CANDY_FILL -> t = CENTRE.clone();
            default -> throw new IllegalArgumentException(piece + " is not a persistent piece");
        }
        t = add(t, new double[] {pose.rootXUnits() / 16, 0, 0});
        double[] qf = qAxis('y', facing.yawDeg);
        return new Xform(rotate(qf, t), qMul(qMul(qf, q), FLIP_Q));
    }

    /** Where a model-space point (units) of a persistent piece lands in the world, root-local blocks: the client draws T + q (F v). */
    public static double[] worldPoint(VaultPiece piece, VaultPose pose, VaultFacing facing, double[] modelPoint) {
        Xform x = piece(piece, pose, facing);
        double[] v = {(modelPoint[0] - 8) / 16, (modelPoint[1] - 8) / 16, (modelPoint[2] - 8) / 16};
        return add(x.t(), rotate(x.q(), VaultMath.flip(v)));
    }
}
