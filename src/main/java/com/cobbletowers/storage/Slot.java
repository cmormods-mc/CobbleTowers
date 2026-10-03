package com.cobbletowers.storage;

/**
 * A place a Pokemon can sit: a party position, or a position in one of the PC's boxes.
 *
 * <p>Plain numbers on purpose -- this is what the party journal writes to disk, and it must never hold a
 * Cobblemon object. For a party slot {@code index} is the slot and {@code sub} is 0; for a box slot
 * {@code index} is the box and {@code sub} is the position in it.
 */
public record Slot(Kind kind, int index, int sub) {

    public enum Kind { PARTY, PC }

    public static Slot party(int slot) {
        return new Slot(Kind.PARTY, slot, 0);
    }

    public static Slot pc(int box, int slot) {
        return new Slot(Kind.PC, box, slot);
    }

    public boolean isParty() {
        return kind == Kind.PARTY;
    }
}
