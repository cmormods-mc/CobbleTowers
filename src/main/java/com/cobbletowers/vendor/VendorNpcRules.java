package com.cobbletowers.vendor;

import java.util.Optional;
import java.util.UUID;

/** The pure half of the physical vendor (P28): how a vendor mob is marked with its run and who may use it. */
public final class VendorNpcRules {

    /** Every vendor mob carries this scoreboard tag. */
    public static final String VENDOR_TAG = "cobbletowers_vendor";

    private static final String RUN_PREFIX = "cobbletowers_vendor_run:";

    public enum Access { ALLOWED, NOT_IN_THIS_RUN, CLOSED }

    private VendorNpcRules() {}

    public static String runTag(UUID runId) {
        return RUN_PREFIX + runId;
    }

    /** The run a tag names, empty for any other tag or a malformed id. */
    public static Optional<UUID> runOf(String tag) {
        if (!tag.startsWith(RUN_PREFIX)) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(tag.substring(RUN_PREFIX.length())));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Whether a player may use a vendor.
     * @param vendorRun the run it was spawned for
     * @param playerRun the player's run, if any
     * @param intermission whether that run is at an intermission
     */
    public static Access access(UUID vendorRun, Optional<UUID> playerRun, boolean intermission) {
        if (playerRun.isEmpty() || !playerRun.get().equals(vendorRun)) return Access.NOT_IN_THIS_RUN;
        return intermission ? Access.ALLOWED : Access.CLOSED;
    }

    /** The sentence shown to a player who may not use the vendor. */
    public static String describe(Access access) {
        return switch (access) {
            case ALLOWED -> "";
            case NOT_IN_THIS_RUN -> "This vendor is serving another team.";
            case CLOSED -> "The vendor is only open between floors.";
        };
    }

    /** The yaw that makes something at (fromX, fromZ) face (toX, toZ), in Minecraft's convention (0 = south). */
    public static float yawToward(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }
}
