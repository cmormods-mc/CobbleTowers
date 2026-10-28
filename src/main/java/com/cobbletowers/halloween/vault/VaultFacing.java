package com.cobbletowers.halloween.vault;

/** Which way the vault's front faces: {@link #yawDeg} is the rotation about +y that takes the model front (-z, north) to that side. */
public enum VaultFacing {
    NORTH(0), WEST(90), SOUTH(180), EAST(-90);

    public final double yawDeg;

    VaultFacing(double yawDeg) {
        this.yawDeg = yawDeg;
    }

    /** From a horizontal direction name as Minecraft spells it ("north", "east", ...), case-insensitive. */
    public static VaultFacing parse(String name) {
        return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
