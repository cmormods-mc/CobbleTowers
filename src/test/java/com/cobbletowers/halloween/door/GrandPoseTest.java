package com.cobbletowers.halloween.door;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GrandPoseTest {

    @Test
    @DisplayName("the static frame ignores the opening angle")
    void frameStays() {
        assertEquals(DoorPose.grand(2, 0, 0), DoorPose.grand(2, 0, 90));
    }

    @Test
    @DisplayName("each leaf turns about its own hinge and the two turn opposite ways")
    void leavesSwing() {
        Matrix4f shut = DoorPose.grand(0, -1, 0);
        Matrix4f left = DoorPose.grand(0, -1, 90);
        Matrix4f right = DoorPose.grand(0, 1, 90);
        assertNotEquals(shut, left);
        assertNotEquals(left, right);
        assertEquals(shut, DoorPose.grand(0, -1, 0));
        // the same angle shut is the same pose for both leaves apart from the hinge offset: both are quarter-size models scaled by 4
        assertEquals(4f, DoorPose.grand(0, 1, 0).getScale(new org.joml.Vector3f()).x, 1e-4f);
    }
}
