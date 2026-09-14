package com.cobbletowers.persistence;

import com.cobbletowers.structure.TowerCellProgress;
import com.cobbletowers.structure.TowerCellState;
import com.cobbletowers.structure.TowerStructureScheduler;
import net.minecraft.nbt.CompoundTag;

/** Pure strict NBT codec for restart-stable Tower structure work snapshots. */
public final class TowerStructureWorkNbtCodec {
    private TowerStructureWorkNbtCodec() {}

    public static CompoundTag encode(TowerStructureScheduler.CellWorkSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("run_id", snapshot.runId());
        tag.putInt("slot_index", snapshot.slotIndex());
        tag.putString("cell_state", snapshot.progress().state().name());
        tag.putInt("section_index", snapshot.progress().sectionIndex());
        tag.putInt("failed_attempts", snapshot.progress().failedAttempts());
        return tag;
    }

    public static TowerStructureScheduler.CellWorkSnapshot decode(CompoundTag tag) {
        TowerCellState state;
        try {
            state = TowerCellState.valueOf(tag.getString("cell_state"));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown Tower cell state: " + tag.getString("cell_state"), ex);
        }

        TowerCellProgress progress = new TowerCellProgress(
                state,
                tag.getInt("section_index"),
                tag.getInt("failed_attempts")
        );
        return new TowerStructureScheduler.CellWorkSnapshot(
                tag.getUUID("run_id"),
                tag.getInt("slot_index"),
                progress
        );
    }
}
