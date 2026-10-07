package com.cobbletowers.storage;

/**
 * A place a Pokemon can sit: a party position or a position in a PC box. Plain numbers, since the party journal
 * writes it to disk. For a party slot {@code index} is the slot and {@code sub} 0; for a box slot {@code index} is
 * the box and {@code sub} the position.
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
