package com.cobbletowers.halloween.door;

/** The door swing in server ticks (ported from the Hallowbound door): a 12-tick Hermite opening that overshoots, a 10-tick smoothstep closing. A reversal starts from the current angle. */
public final class DoorMotion {

    public enum State { CLOSED, OPENING, OPEN, CLOSING }

    public static final int OPEN_TICKS = 12, CLOSE_TICKS = 10;
    private static final double[] TIMES = {0, 2, 5, 8, 12};
    private static final double[] VALUES = {0, 26, 105, 87, 90}, SLOPES = {0, 20, 0, 0, 0};

    private State state;
    private boolean targetOpen;
    private double angle, start;
    private int tick;

    public DoorMotion(boolean open) {
        targetOpen = open;
        angle = open ? 90 : 0;
        state = open ? State.OPEN : State.CLOSED;
    }

    public void setOpen(boolean open) {
        if (open == targetOpen) return;
        targetOpen = open;
        start = angle;
        tick = 0;
        state = open ? State.OPENING : State.CLOSING;
    }

    /** Advances one tick. @return whether the angle changed */
    public boolean tick() {
        if (state == State.CLOSED || state == State.OPEN) return false;
        tick++;
        if (state == State.OPENING) {
            angle = start + (90 - start) * openingDegrees(tick) / 90;
            if (tick >= OPEN_TICKS) {
                angle = 90;
                state = State.OPEN;
            }
        } else {
            double u = Math.min(1, tick / (double) CLOSE_TICKS);
            angle = start * (1 - u * u * (3 - 2 * u));
            if (tick >= CLOSE_TICKS) {
                angle = 0;
                state = State.CLOSED;
            }
        }
        return true;
    }

    public double angle() {
        return angle;
    }

    public boolean targetOpen() {
        return targetOpen;
    }

    public State state() {
        return state;
    }

    /** The opening curve, 0 to 90 degrees with an overshoot to 105, at tick {@code t}. */
    public static double openingDegrees(double t) {
        if (t <= 0) return 0;
        if (t >= OPEN_TICKS) return 90;
        for (int i = 0; i < TIMES.length - 1; i++) {
            if (t <= TIMES[i + 1]) {
                double length = TIMES[i + 1] - TIMES[i], u = (t - TIMES[i]) / length, u2 = u * u, u3 = u2 * u;
                return (2 * u3 - 3 * u2 + 1) * VALUES[i] + (u3 - 2 * u2 + u) * length * SLOPES[i]
                        + (-2 * u3 + 3 * u2) * VALUES[i + 1] + (u3 - u2) * length * SLOPES[i + 1];
            }
        }
        throw new AssertionError();
    }
}
