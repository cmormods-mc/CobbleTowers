package com.cobbletowers.persistence;

import com.cobbletowers.CobbleTowers;
import com.cobbletowers.run.TowerRunSnapshot;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Versioned server-global persistence for recoverable Tower runs and teardown-pending terminal runs.
 *
 * <p>Only immutable run snapshots are stored. Individual malformed runs are isolated during load so
 * one damaged run cannot prevent healthy parties from recovering. A terminal run remains persisted
 * until world/entity teardown and reward commitment have completed and the run manager explicitly
 * releases it; this prevents a crash between terminal transition and cleanup from restoring stale
 * active state. Unknown newer root schemas are preserved without modification.
 */
public final class TowerSavedData extends SavedData {
    public static final String STORAGE_ID = "cobbletowers_runs";
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String RUNS = "runs";

    private final Map<UUID, TowerRunSnapshot> runs = new LinkedHashMap<>();
    private final int loadedSchemaVersion;
    private final CompoundTag preservedUnknownRoot;

    public TowerSavedData() {
        this(CURRENT_SCHEMA_VERSION, null);
    }

    private TowerSavedData(int loadedSchemaVersion, CompoundTag preservedUnknownRoot) {
        this.loadedSchemaVersion = loadedSchemaVersion;
        this.preservedUnknownRoot = preservedUnknownRoot;
    }

    public static Factory<TowerSavedData> factory() {
        // DataFixTypes must be non-null in 1.21.1. CobbleTowers owns its own explicit schema migration;
        // this vanilla fixer family satisfies DimensionDataStorage's read contract.
        return new Factory<>(TowerSavedData::new, TowerSavedData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    }

    public static TowerSavedData load(CompoundTag root, HolderLookup.Provider registries) {
        int schemaVersion = root.getInt(SCHEMA_VERSION);
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            CobbleTowers.LOGGER.error(
                    "CobbleTowers save schema {} is newer than supported schema {}. Preserving it without modification.",
                    schemaVersion,
                    CURRENT_SCHEMA_VERSION
            );
            return new TowerSavedData(schemaVersion, root.copy());
        }
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "Unsupported CobbleTowers save schema " + schemaVersion + "; supported schema is " + CURRENT_SCHEMA_VERSION);
        }

        TowerSavedData data = new TowerSavedData(schemaVersion, null);
        ListTag runTags = root.getList(RUNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < runTags.size(); i++) {
            CompoundTag runTag = runTags.getCompound(i);
            try {
                TowerRunSnapshot snapshot = TowerRunNbtCodec.decode(runTag);
                if (data.runs.put(snapshot.runId(), snapshot) != null) {
                    throw new IllegalArgumentException("Duplicate persisted run UUID: " + snapshot.runId());
                }
            } catch (RuntimeException ex) {
                String runHint = runTag.hasUUID("run_id") ? runTag.getUUID("run_id").toString() : "<unknown>";
                CobbleTowers.LOGGER.error(
                        "Skipping corrupt Tower run {} while loading persistence; other runs will continue.",
                        runHint,
                        ex
                );
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        if (incompatibleNewerSchema()) {
            return preservedUnknownRoot.copy();
        }

        root.putInt(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        ListTag runTags = new ListTag();
        runs.values().stream()
                .sorted(Comparator.comparing(snapshot -> snapshot.runId().toString()))
                .map(TowerRunNbtCodec::encode)
                .forEach(runTags::add);
        root.put(RUNS, runTags);
        return root;
    }

    public boolean incompatibleNewerSchema() {
        return loadedSchemaVersion > CURRENT_SCHEMA_VERSION;
    }

    public int loadedSchemaVersion() {
        return loadedSchemaVersion;
    }

    public List<TowerRunSnapshot> snapshots() {
        return List.copyOf(runs.values());
    }

    public boolean put(TowerRunSnapshot snapshot) {
        requireCompatible();
        Objects.requireNonNull(snapshot, "snapshot");
        TowerRunSnapshot previous = runs.put(snapshot.runId(), snapshot);
        if (snapshot.equals(previous)) return false;
        setDirty();
        return true;
    }

    public boolean remove(UUID runId) {
        requireCompatible();
        Objects.requireNonNull(runId, "runId");
        if (runs.remove(runId) == null) return false;
        setDirty();
        return true;
    }

    public boolean replaceAll(Collection<TowerRunSnapshot> snapshots) {
        requireCompatible();
        Objects.requireNonNull(snapshots, "snapshots");

        Map<UUID, TowerRunSnapshot> replacement = new LinkedHashMap<>();
        for (TowerRunSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "snapshots may not contain null");
            if (replacement.put(snapshot.runId(), snapshot) != null) {
                throw new IllegalArgumentException("Duplicate Tower run UUID: " + snapshot.runId());
            }
        }
        if (runs.equals(replacement)) return false;
        runs.clear();
        runs.putAll(replacement);
        setDirty();
        return true;
    }

    private void requireCompatible() {
        if (incompatibleNewerSchema()) {
            throw new IllegalStateException(
                    "CobbleTowers persistence schema " + loadedSchemaVersion
                            + " is newer than supported schema " + CURRENT_SCHEMA_VERSION);
        }
    }
}
