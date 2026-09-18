package com.cobbletowers.persistence;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/**
 * Why a cell is out of circulation, and since when.
 *
 * <p>The reason is kept because a quarantined cell is a message to an operator, not just a flag: the
 * useful question is always "what was left in it", and a cell that is simply marked bad tells nobody
 * whether it is safe to clear.
 */
public record CellQuarantine(int cell, String reason, long since) {

    public CellQuarantine {
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) throw new IllegalArgumentException("a quarantine needs a reason");
        if (cell < 0) throw new IllegalArgumentException("cell must be >= 0, got " + cell);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cell", cell);
        tag.putString("reason", reason);
        tag.putLong("since", since);
        return tag;
    }

    public static CellQuarantine fromTag(CompoundTag tag) {
        return new CellQuarantine(tag.getInt("cell"), tag.getString("reason"), tag.getLong("since"));
    }
}
