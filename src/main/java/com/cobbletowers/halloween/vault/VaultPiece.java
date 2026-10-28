package com.cobbletowers.halloween.vault;

/** The runtime model pieces. The first four are persistent display entities; the reveals are spawned after the lid is open. */
public enum VaultPiece {
    BODY("body", true), LID("lid", true), FACE_PLATE("face_plate", true), CANDY_FILL("candy_fill", true),
    REVEAL_A("reveal_a", false), REVEAL_B("reveal_b", false), REVEAL_C("reveal_c", false);

    /** Model path under {@code cobbletowers:halloween/jack_o_lantern_vault/}. */
    public final String model;
    public final boolean persistent;

    VaultPiece(String model, boolean persistent) {
        this.model = model;
        this.persistent = persistent;
    }
}
