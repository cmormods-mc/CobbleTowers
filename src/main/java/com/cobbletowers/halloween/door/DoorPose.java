package com.cobbletowers.halloween.door;

import org.joml.Matrix4f;

/** Model-to-display transform in blocks for the right-hinge door (ported from the Hallowbound door); the model hinge edge is X=0. */
public final class DoorPose {

    private DoorPose() {}

    /**
     * @param quarterTurns 0 south, 1 east, 2 north, 3 west
     * @param degrees the wing opening angle
     * @param moving whether this part swings (the frame never does)
     */
    public static Matrix4f matrix(int quarterTurns, double degrees, boolean moving) {
        float offset = 1.5f / 16, z = 1.5f / 16;
        float angle = moving ? (float) Math.toRadians(-degrees) : 0;
        return new Matrix4f().translate(.5f, 0, .5f).rotateY((float) (quarterTurns * Math.PI / 2))
                .translate(offset - .5f, 0, z - .5f).rotateY(angle).translate(.5f, .5f, .5f - z)
                // cancels the 1.21.1 item display half-turn about the model centre
                .rotateY((float) Math.PI);
    }

    /**
     * The Grand door (ported from the Hallowbound grand door): the Java models are quarter size, so the matrix scales by 4; the origin is the
     * bottom-centre cell of the 5-wide footprint, whose front is -Z.
     * @param side -1 left leaf, +1 right leaf, 0 the static frame
     * @param degrees the leaf opening angle
     */
    public static Matrix4f grand(int quarterTurns, int side, double degrees) {
        float pivot = side < 0 ? 1 : 4;
        float rotation = (float) Math.toRadians(side == 0 ? 0 : -side * degrees);
        return new Matrix4f().translation(.5f, 0, .5f).rotateY((float) (quarterTurns * Math.PI / 2))
                .translate(-2.5f, 0, -.5f)
                .translate(pivot, 0, 1.5f / 16).rotateY(rotation).translate(-pivot, 0, -1.5f / 16)
                .scale(4).translate(.5f, .5f, .5f).rotateY((float) Math.PI);
    }
}
