package com.cobbletowers.persistence;

import com.cobbletowers.TowerLog;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which cells are out of circulation, and why.
 *
 * <p>A separate file from the runs on purpose: a quarantine outlives the run that caused it. That is
 * the whole point of TDS #35 -- the run ends either way, and the cell must not come back into use
 * until someone has established it is clean. Storing it on the run would lose it exactly when the run
 * is retired.
 *
 * <p>Leases are not stored here. A lease belongs to its run and is written with it, so there is one
 * place a cell's tenancy can be read from and no way for two files to disagree about who holds what.
 */
public final class CellStateStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_cells";
    private static final String QUARANTINED = "quarantined";

    private final Map<Integer, CellQuarantine> quarantined = new LinkedHashMap<>();

    public static SavedData.Factory<CellStateStore> factory() {
        return new SavedData.Factory<>(CellStateStore::new, CellStateStore::load, DataFixTypes.LEVEL);
    }

    public static CellStateStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    /** Every quarantined cell, by index. */
    public Map<Integer, CellQuarantine> quarantined() {
        return Map.copyOf(quarantined);
    }

    public boolean isQuarantined(int cell) {
        return quarantined.containsKey(cell);
    }

    /**
     * Puts a cell out of circulation, writing it to disk immediately.
     *
     * <p>Flushed rather than left dirty for the same reason a run checkpoint is: the failure that
     * caused the quarantine is exactly the kind of event a crash tends to follow, and a quarantine
     * lost in that crash hands a dirty cell to the next run.
     */
    public void quarantine(MinecraftServer server, CellQuarantine entry) {
        quarantined.put(entry.cell(), entry);
        setDirty();
        server.overworld().getDataStorage().save();
        TowerLog.warn("Cell {} quarantined: {}", entry.cell(), entry.reason());
    }

    /** Returns a cell to circulation. Returns false when it was not quarantined. */
    public boolean clear(MinecraftServer server, int cell) {
        if (quarantined.remove(cell) == null) return false;
        setDirty();
        server.overworld().getDataStorage().save();
        TowerLog.info("Cell {} cleared and returned to service", cell);
        return true;
    }

    /** Package-private rather than private so a test can round-trip the file without a server. */
    static CellStateStore load(CompoundTag tag, HolderLookup.Provider registries) {
        CellStateStore store = new CellStateStore();
        ListTag entries = tag.getList(QUARANTINED, Tag.TAG_COMPOUND);
        int dropped = 0;
        for (int i = 0; i < entries.size(); i++) {
            try {
                CellQuarantine entry = CellQuarantine.fromTag(entries.getCompound(i));
                store.quarantined.put(entry.cell(), entry);
            } catch (RuntimeException ex) {
                // Dropping a quarantine silently would return a dirty cell to service, so say so.
                dropped++;
                TowerLog.error("Unreadable cell quarantine entry discarded: {}", ex.toString());
            }
        }
        if (dropped > 0) {
            TowerLog.error("{} cell quarantine(s) could not be read; those cells are back in service"
                    + " and should be checked by hand.", dropped);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (CellQuarantine entry : quarantined.values()) entries.add(entry.toTag());
        tag.put(QUARANTINED, entries);
        return tag;
    }
}
