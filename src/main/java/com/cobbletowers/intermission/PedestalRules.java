package com.cobbletowers.intermission;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The pure half of the modifier pedestals (P55): tags, where each stands, and which one a player's gaze falls on. */
public final class PedestalRules {

    public static final String TAG = "cobbletowers_pedestal";
    public static final String PART_STAND = "cobbletowers_pedestal_stand";
    public static final String PART_TEXT = "cobbletowers_pedestal_text";
    public static final String PART_HITBOX = "cobbletowers_pedestal_hitbox";

    private static final String RUN_PREFIX = "cobbletowers_pedestal_run:";
    private static final String CARD_PREFIX = "cobbletowers_pedestal_card_";

    /** How far from the vendor the row stands, and the gap between pedestal centres. */
    static final double ROW_DISTANCE = 4.0;
    static final double SPACING = 3.4;

    /** The click and gaze box: wide enough to catch the hologram, short of the next pedestal. */
    public static final double HITBOX_WIDTH = 2.4;
    public static final double HITBOX_HEIGHT = 3.0;
    public static final double GAZE_RANGE = 6.0;

    private PedestalRules() {}

    /** Floor-level centre of one pedestal. */
    public record Slot(double x, double y, double z) {}

    public static String runTag(UUID runId) {
        return RUN_PREFIX + runId;
    }

    public static String cardTag(int index) {
        return CARD_PREFIX + index;
    }

    public static Optional<UUID> runOf(String tag) {
        if (!tag.startsWith(RUN_PREFIX)) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(tag.substring(RUN_PREFIX.length())));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static Optional<Integer> cardOf(String tag) {
        if (!tag.startsWith(CARD_PREFIX)) return Optional.empty();
        try {
            int index = Integer.parseInt(tag.substring(CARD_PREFIX.length()));
            return index < 0 ? Optional.empty() : Optional.of(index);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /**
     * {@code count} pedestals in a row {@link #ROW_DISTANCE} from the vendor toward the point it looks at, spread
     * across that line and centred on it. Order runs left to right as seen from the vendor.
     */
    public static List<Slot> layout(double vendorX, double vendorZ, double towardX, double towardZ, double floorY, int count) {
        double dx = towardX - vendorX;
        double dz = towardZ - vendorZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-6) {
            dx = -1;
            dz = 0;
            length = 1;
        }
        dx /= length;
        dz /= length;
        double px = dz;
        double pz = -dx;
        List<Slot> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double offset = (i - (count - 1) / 2.0) * SPACING;
            slots.add(new Slot(vendorX + dx * ROW_DISTANCE + px * offset, floorY, vendorZ + dz * ROW_DISTANCE + pz * offset));
        }
        return slots;
    }

    /**
     * The pedestal the ray from {@code eye} along {@code look} meets first inside {@link #GAZE_RANGE}, or empty. Slab
     * test against each pedestal's upright box.
     */
    public static Optional<Integer> gazedAt(double eyeX, double eyeY, double eyeZ, double lookX, double lookY, double lookZ,
                                            List<Slot> slots) {
        int best = -1;
        double bestDistance = GAZE_RANGE;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            double half = HITBOX_WIDTH / 2;
            double tMin = 0;
            double tMax = bestDistance;
            double[] origin = {eyeX, eyeY, eyeZ};
            double[] dir = {lookX, lookY, lookZ};
            double[] low = {s.x() - half, s.y(), s.z() - half};
            double[] high = {s.x() + half, s.y() + HITBOX_HEIGHT, s.z() + half};
            boolean hit = true;
            for (int axis = 0; axis < 3 && hit; axis++) {
                if (Math.abs(dir[axis]) < 1.0e-9) {
                    hit = origin[axis] >= low[axis] && origin[axis] <= high[axis];
                    continue;
                }
                double t1 = (low[axis] - origin[axis]) / dir[axis];
                double t2 = (high[axis] - origin[axis]) / dir[axis];
                tMin = Math.max(tMin, Math.min(t1, t2));
                tMax = Math.min(tMax, Math.max(t1, t2));
                hit = tMin <= tMax;
            }
            if (hit && tMin < bestDistance) {
                best = i;
                bestDistance = tMin;
            }
        }
        return best < 0 ? Optional.empty() : Optional.of(best);
    }
}
