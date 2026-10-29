package com.cobbletowers.halloween.vault;

/**
 * The animated channels of the Jack-o'-Lantern Vault at one tick: the lid's hinge angle (degrees, positive lifts the front), the face plate's
 * latch press (model units, +z into the body) and drop (y), and a whole-vault shake in x and y (model units, vault space).
 */
public record VaultPose(double lidDeg, double plateUnits, double rootXUnits, double plateYUnits, double rootYUnits) {

    public static final VaultPose REST = new VaultPose(0, 0, 0);

    public VaultPose(double lidDeg, double plateUnits, double rootXUnits) {
        this(lidDeg, plateUnits, rootXUnits, 0, 0);
    }
}
