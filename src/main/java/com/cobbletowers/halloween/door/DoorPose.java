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
}
