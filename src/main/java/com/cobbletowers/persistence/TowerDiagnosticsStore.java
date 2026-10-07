package com.cobbletowers.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * TDS #60's structured diagnostics: a rolling aggregate per timing category and one snapshot per run. Telemetry, not
 * logical state (TDS section 10). {@link #runs} is capped at {@link #MAX_RUN_ENTRIES}, evicting the least recently
 * updated.
 */
public final class TowerDiagnosticsStore extends TowerStore {

    private static final String FILE_ID = "cobbletowers_diagnostics";
    private static final String CATEGORIES = "categories";
    private static final String CATEGORY_NAME = "category";
    private static final String RUNS = "runs";
    private static final String RUN_ID = "run";
    private static final String NON_CHECKPOINTED = "non_checkpointed_writes";

    /** Past this many tracked runs, the least-recently-updated entry is dropped for the new one. */
    public static final int MAX_RUN_ENTRIES = 1000;

    private final Map<String, CategoryStats> categories = new LinkedHashMap<>();
    private final Map<UUID, RunDiagnostics> runs = new LinkedHashMap<>();
    private int nonCheckpointedWrites;

    /** One timing category's running aggregate -- a rolling total, not a sample list (see the design doc). */
    public record CategoryStats(long count, long sum, long min, long max, long budgetExceededCount) {

        public static final CategoryStats EMPTY = new CategoryStats(0, 0, Long.MAX_VALUE, 0, 0);

        CategoryStats sample(long millis, boolean overBudget) {
            return new CategoryStats(count + 1, sum + millis, Math.min(min, millis), Math.max(max, millis),
                    budgetExceededCount + (overBudget ? 1 : 0));
        }

        public long averageMillis() {
            return count == 0 ? 0 : sum / count;
        }
    }

    /** One run's own diagnostic snapshot -- the "keyed by run" half of TDS #60. */
    public record RunDiagnostics(long lastTransitionMillis, long lastEncounterConstructionMillis, long updatedAt) {
        public static final RunDiagnostics EMPTY = new RunDiagnostics(0, 0, 0);
    }

    /** The store for this server. Created empty on a world that has never recorded a sample. */
    public static TowerDiagnosticsStore get(MinecraftServer server) {
        return open(server, TowerDiagnosticsStore::new, TowerDiagnosticsStore::load, FILE_ID);
    }

    public CategoryStats categoryStats(String category) {
        return categories.getOrDefault(category, CategoryStats.EMPTY);
    }

    public Map<String, CategoryStats> allCategories() {
        return Map.copyOf(categories);
    }

    public RunDiagnostics runDiagnostics(UUID runId) {
        return runs.getOrDefault(runId, RunDiagnostics.EMPTY);
    }

    public int nonCheckpointedWrites() {
        return nonCheckpointedWrites;
    }

    /** Records one sample against a category's rolling aggregate. */
    public void sample(String category, long millis, boolean overBudget) {
        categories.merge(category, CategoryStats.EMPTY.sample(millis, overBudget),
                (existing, fresh) -> existing.sample(millis, overBudget));
        setDirty();
    }

    /**
     * Updates one run's transition timing, evicting the oldest entry first if this run is new and the map is full.
     */
    public void recordTransition(UUID runId, long millis, long now) {
        updateRun(runId, existing -> new RunDiagnostics(millis, existing.lastEncounterConstructionMillis(), now));
    }

    public void recordEncounterConstruction(UUID runId, long millis, long now) {
        updateRun(runId, existing -> new RunDiagnostics(existing.lastTransitionMillis(), millis, now));
    }

    private void updateRun(UUID runId, java.util.function.UnaryOperator<RunDiagnostics> update) {
        if (!runs.containsKey(runId) && runs.size() >= MAX_RUN_ENTRIES) {
            UUID oldest = null;
            long oldestAt = Long.MAX_VALUE;
            for (Map.Entry<UUID, RunDiagnostics> entry : runs.entrySet()) {
                if (entry.getValue().updatedAt() < oldestAt) {
                    oldestAt = entry.getValue().updatedAt();
                    oldest = entry.getKey();
                }
            }
            if (oldest != null) runs.remove(oldest);
        }
        runs.merge(runId, update.apply(RunDiagnostics.EMPTY), (existing, ignored) -> update.apply(existing));
        setDirty();
    }

    public void recordNonCheckpointedWrite() {
        nonCheckpointedWrites++;
        setDirty();
    }

    public void recordCheckpoint() {
        if (nonCheckpointedWrites == 0) return;
        nonCheckpointedWrites = 0;
        setDirty();
    }

    static TowerDiagnosticsStore load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerDiagnosticsStore store = new TowerDiagnosticsStore();
        ListTag categoryList = tag.getList(CATEGORIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < categoryList.size(); i++) {
            CompoundTag entry = categoryList.getCompound(i);
            store.categories.put(entry.getString(CATEGORY_NAME), new CategoryStats(
                    entry.getLong("count"), entry.getLong("sum"), entry.getLong("min"), entry.getLong("max"),
                    entry.getLong("over_budget")));
        }
        ListTag runList = tag.getList(RUNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < runList.size(); i++) {
            CompoundTag entry = runList.getCompound(i);
            store.runs.put(entry.getUUID(RUN_ID), new RunDiagnostics(
                    entry.getLong("last_transition_millis"), entry.getLong("last_encounter_construction_millis"),
                    entry.getLong("updated_at")));
        }
        store.nonCheckpointedWrites = tag.getInt(NON_CHECKPOINTED);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag categoryList = new ListTag();
        for (Map.Entry<String, CategoryStats> entry : categories.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(CATEGORY_NAME, entry.getKey());
            CategoryStats stats = entry.getValue();
            entryTag.putLong("count", stats.count());
            entryTag.putLong("sum", stats.sum());
            entryTag.putLong("min", stats.min());
            entryTag.putLong("max", stats.max());
            entryTag.putLong("over_budget", stats.budgetExceededCount());
            categoryList.add(entryTag);
        }
        tag.put(CATEGORIES, categoryList);

        ListTag runList = new ListTag();
        for (Map.Entry<UUID, RunDiagnostics> entry : runs.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(RUN_ID, entry.getKey());
            RunDiagnostics diagnostics = entry.getValue();
            entryTag.putLong("last_transition_millis", diagnostics.lastTransitionMillis());
            entryTag.putLong("last_encounter_construction_millis", diagnostics.lastEncounterConstructionMillis());
            entryTag.putLong("updated_at", diagnostics.updatedAt());
            runList.add(entryTag);
        }
        tag.put(RUNS, runList);
        tag.putInt(NON_CHECKPOINTED, nonCheckpointedWrites);
        return tag;
    }
}
