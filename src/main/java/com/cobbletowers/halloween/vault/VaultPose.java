package com.cobbletowers.halloween.vault;

/**
 * The three animated channels of the Jack-o'-Lantern Vault at one tick: the lid's hinge angle (degrees, positive lifts the front),
 * the face plate's latch press (model units, +z into the body) and a whole-vault x shake (model units, vault space).
 */
public record VaultPose(double lidDeg, double plateUnits, double rootXUnits) {

    public static final VaultPose REST = new VaultPose(0, 0, 0);
}
