package com.cobbletowers.persistence;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Where the season calendar has got to (P36a): the newest season finalised, the finalisation in progress (so a crash resumes at the
 * next step and no step runs twice), and the newest season whose start was announced.
 */
public final class TowerSeasonStore extends SavedData {

    private static final String FILE_ID = "cobbletowers_seasons";

    /** The newest season whose Hall entry and pruning are complete. */
    private int lastFinalized;
    /** The season being finalised and how many of its steps are done (0 when none is). */
    private int inProgressSeason;
    private int inProgressSteps;
    private int lastAnnouncedStart;

    public static SavedData.Factory<TowerSeasonStore> factory() {
        return new SavedData.Factory<>(TowerSeasonStore::new, TowerSeasonStore::load, DataFixTypes.LEVEL);
    }

    public static TowerSeasonStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public int lastFinalized() {
        return lastFinalized;
    }

    /** How many steps of {@code season}'s finalisation are done (0 if it has not begun, or another season is in progress). */
    public int stepsDone(int season) {
        return inProgressSeason == season ? inProgressSteps : 0;
    }

    public void stepDone(int season, int steps) {
        inProgressSeason = season;
        inProgressSteps = steps;
        setDirty();
    }

    /** Finalisation of {@code season} is complete. */
    public void finalized(int season) {
        lastFinalized = Math.max(lastFinalized, season);
        inProgressSeason = 0;
        inProgressSteps = 0;
        setDirty();
    }

    public int lastAnnouncedStart() {
        return lastAnnouncedStart;
    }

    public void announcedStart(int season) {
        lastAnnouncedStart = Math.max(lastAnnouncedStart, season);
        setDirty();
    }

    /** An operator's reset, and what a test uses to start clean. */
    public void clear() {
        lastFinalized = 0;
        inProgressSeason = 0;
        inProgressSteps = 0;
        lastAnnouncedStart = 0;
        setDirty();
    }

    public void checkpoint(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("last_finalized", lastFinalized);
        tag.putInt("in_progress_season", inProgressSeason);
        tag.putInt("in_progress_steps", inProgressSteps);
        tag.putInt("last_announced_start", lastAnnouncedStart);
        return tag;
    }

    public static TowerSeasonStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerSeasonStore store = new TowerSeasonStore();
        store.lastFinalized = tag.getInt("last_finalized");
        store.inProgressSeason = tag.getInt("in_progress_season");
        store.inProgressSteps = tag.getInt("in_progress_steps");
        store.lastAnnouncedStart = tag.getInt("last_announced_start");
        return store;
    }
}
