package com.cobbletowers.persistence;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Base of the tower's per-world stores: opens the file in the overworld's data storage and can flush it at once. */
public abstract class TowerStore extends SavedData {

    protected static <T extends TowerStore> T open(MinecraftServer server, Supplier<T> create,
            BiFunction<CompoundTag, HolderLookup.Provider, T> load, String fileId) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(create, load::apply, DataFixTypes.LEVEL), fileId);
    }

    /** Writes to disk now instead of at the next autosave, for changes a crash must not lose. */
    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }
}
