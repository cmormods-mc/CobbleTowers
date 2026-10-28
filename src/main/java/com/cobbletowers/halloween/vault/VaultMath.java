package com.cobbletowers.halloween.vault;

/** Minimal double-precision vector and quaternion maths (xyzw, Hamilton), so the pose maths is testable without Minecraft or joml. */
public final class VaultMath {

    private VaultMath() {}

    public static double[] qAxis(char axis, double deg) {
        double h = Math.toRadians(deg) / 2, s = Math.sin(h), c = Math.cos(h);
        return switch (axis) {
            case 'x' -> new double[] {s, 0, 0, c};
            case 'y' -> new double[] {0, s, 0, c};
            case 'z' -> new double[] {0, 0, s, c};
            default -> throw new IllegalArgumentException("axis " + axis);
        };
    }

    public static double[] qMul(double[] a, double[] b) {
        return new double[] {
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    /** Rotates v by the unit quaternion q. */
    public static double[] rotate(double[] q, double[] v) {
        double[] qv = {q[0], q[1], q[2]};
        double[] t = cross(qv, v);
        t[0] *= 2; t[1] *= 2; t[2] *= 2;
        double[] u = cross(qv, t);
        return new double[] {v[0] + q[3] * t[0] + u[0], v[1] + q[3] * t[1] + u[1], v[2] + q[3] * t[2] + u[2]};
    }

    public static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** The client's half turn of the item model about its own centre axis (rotY 180): x and z change sign. */
    public static double[] flip(double[] v) {
        return new double[] {-v[0], v[1], -v[2]};
    }

    public static double[] add(double[] a, double[] b) {
        return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    public static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }
}
