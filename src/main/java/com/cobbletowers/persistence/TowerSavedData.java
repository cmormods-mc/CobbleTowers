package com.cobbletowers.persistence;

import com.cobbletowers.CobbleTowers;
import com.cobbletowers.run.TowerRunSnapshot;
import com.cobbletowers.structure.TowerStructureScheduler;
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
 * Versioned server-global persistence for recoverable Tower runs and structure work.
 *
 * <p>Run snapshots and structure-work cursors are stored separately and keyed by run UUID. Individual
 * malformed entries are isolated during load so one damaged run/cell cannot prevent healthy parties
 * from recovering. Terminal runs remain persisted until teardown/reward commitment and explicit
 * release. Unknown newer schemas are preserved without modification.
 */
public final class TowerSavedData extends SavedData {
    public static final String STORAGE_ID = "cobbletowers_runs";
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String RUNS = "runs";
    private static final String STRUCTURE_WORK = "structure_work";

    private final Map<UUID, TowerRunSnapshot> runs = new LinkedHashMap<>();
    private final Map<UUID, TowerStructureScheduler.CellWorkSnapshot> structureWork = new LinkedHashMap<>();
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
        if (schemaVersion < 1) {
            throw new IllegalStateException("Unsupported CobbleTowers save schema " + schemaVersion);
        }

        TowerSavedData data = new TowerSavedData(schemaVersion, null);
        data.loadRuns(root.getList(RUNS, Tag.TAG_COMPOUND));

        if (schemaVersion >= 2) {
            data.loadStructureWork(root.getList(STRUCTURE_WORK, Tag.TAG_COMPOUND));
        } else {
            // v1 contained only run snapshots. Missing structure metadata is intentionally not guessed
            // here; the world reconciler will rebuild affected live cells idempotently from section 0.
            CobbleTowers.LOGGER.info("Migrating CobbleTowers persistence schema 1 -> 2.");
            data.setDirty();
        }
        return data;
    }

    private void loadRuns(ListTag runTags) {
        for (int i = 0; i < runTags.size(); i++) {
            CompoundTag runTag = runTags.getCompound(i);
            try {
                TowerRunSnapshot snapshot = TowerRunNbtCodec.decode(runTag);
                if (runs.containsKey(snapshot.runId())) {
                    throw new IllegalArgumentException("Duplicate persisted run UUID: " + snapshot.runId());
                }
                runs.put(snapshot.runId(), snapshot);
            } catch (RuntimeException ex) {
                String runHint = runTag.hasUUID("run_id") ? runTag.getUUID("run_id").toString() : "<unknown>";
                CobbleTowers.LOGGER.error(
                        "Skipping corrupt Tower run {} while loading persistence; other runs will continue.",
                        runHint,
                        ex
                );
            }
        }
    }

    private void loadStructureWork(ListTag workTags) {
        for (int i = 0; i < workTags.size(); i++) {
            CompoundTag workTag = workTags.getCompound(i);
            try {
                TowerStructureScheduler.CellWorkSnapshot snapshot = TowerStructureWorkNbtCodec.decode(workTag);
                if (structureWork.containsKey(snapshot.runId())) {
                    throw new IllegalArgumentException("Duplicate persisted structure work UUID: " + snapshot.runId());
                }
                structureWork.put(snapshot.runId(), snapshot);
            } catch (RuntimeException ex) {
                String runHint = workTag.hasUUID("run_id") ? workTag.getUUID("run_id").toString() : "<unknown>";
                CobbleTowers.LOGGER.error(
                        "Skipping corrupt Tower structure work {} while loading; other cells will continue.",
                        runHint,
                        ex
                );
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        if (incompatibleNewerSchema()) return preservedUnknownRoot.copy();

        root.putInt(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);

        ListTag runTags = new ListTag();
        runs.values().stream()
                .sorted(Comparator.comparing(snapshot -> snapshot.runId().toString()))
                .map(TowerRunNbtCodec::encode)
                .forEach(runTags::add);
        root.put(RUNS, runTags);

        ListTag workTags = new ListTag();
        structureWork.values().stream()
                .sorted(Comparator.comparing(snapshot -> snapshot.runId().toString()))
                .map(TowerStructureWorkNbtCodec::encode)
                .forEach(workTags::add);
        root.put(STRUCTURE_WORK, workTags);
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

    public List<TowerStructureScheduler.CellWorkSnapshot> structureWorkSnapshots() {
        return List.copyOf(structureWork.values());
    }

    public boolean put(TowerRunSnapshot snapshot) {
        requireCompatible();
        Objects.requireNonNull(snapshot, "snapshot");
        TowerRunSnapshot previous = runs.put(snapshot.runId(), snapshot);
        if (snapshot.equals(previous)) return false;
        setDirty();
        return true;
    }

    public boolean putStructureWork(TowerStructureScheduler.CellWorkSnapshot snapshot) {
        requireCompatible();
        Objects.requireNonNull(snapshot, "snapshot");
        TowerStructureScheduler.CellWorkSnapshot previous = structureWork.put(snapshot.runId(), snapshot);
        if (snapshot.equals(previous)) return false;
        setDirty();
        return true;
    }

    public boolean removeStructureWork(UUID runId) {
        requireCompatible();
        Objects.requireNonNull(runId, "runId");
        if (structureWork.remove(runId) == null) return false;
        setDirty();
        return true;
    }

    public boolean remove(UUID runId) {
        requireCompatible();
        Objects.requireNonNull(runId, "runId");
        boolean changed = runs.remove(runId) != null;
        changed |= structureWork.remove(runId) != null;
        if (changed) setDirty();
        return changed;
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
