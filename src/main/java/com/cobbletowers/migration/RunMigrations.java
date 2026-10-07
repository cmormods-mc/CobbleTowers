package com.cobbletowers.migration;

import com.cobbletowers.persistence.PersistedRun;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;

/**
 * Moves a stored run to the shape this build reads (TDS #40). The current version is returned as is, an older one is
 * stepped up in order, and anything else (newer or unreachable) is refused.
 */
public final class RunMigrations {

    /** The oldest shape a step chain still exists for. */
    public static final int OLDEST_SUPPORTED = 1;

    /**
     * A step keyed by the version it reads: {@code STEPS.get(n)} turns version n into n + 1 and sets {@code
     * schema_version} itself.
     */
    private static final Map<Integer, UnaryOperator<CompoundTag>> STEPS = new LinkedHashMap<>();

    static {
        // 1 -> 2: instance cell (optional field; the step only stamps the version).
        STEPS.put(1, tag -> {
            tag.putInt("schema_version", 2);
            return tag;
        });
        // 2 -> 3: unclaimed ledger; written empty explicitly.
        STEPS.put(2, tag -> {
            if (!tag.contains("ledger")) tag.put("ledger", new net.minecraft.nbt.ListTag());
            tag.putInt("schema_version", 3);
            return tag;
        });
        // 3 -> 4: drafted modifiers; written explicitly so a migrated file matches a fresh write.
        STEPS.put(3, tag -> {
            if (!tag.contains("modifiers")) {
                tag.put("modifiers", com.cobbletowers.persistence.RunModifierState.EMPTY.toTag());
            }
            tag.putInt("schema_version", 4);
            return tag;
        });
        // 4 -> 5: banked ledger count; an older run banked nothing, so 0.
        STEPS.put(4, tag -> {
            if (!tag.contains("last_banked_floor")) tag.putInt("last_banked_floor", 0);
            tag.putInt("schema_version", 5);
            return tag;
        });
        // 5 -> 6: vendor purchase counts; written explicitly like step 3 -> 4.
        STEPS.put(5, tag -> {
            if (!tag.contains("vendor_purchases")) tag.put("vendor_purchases", new net.minecraft.nbt.ListTag());
            tag.putInt("schema_version", 6);
            return tag;
        });
        // 6 -> 7: run options (playlist and trial); written explicitly like step 3 -> 4.
        STEPS.put(6, tag -> {
            if (!tag.contains("options")) tag.put("options", com.cobbletowers.persistence.RunOptions.NONE.toTag());
            tag.putInt("schema_version", 7);
            return tag;
        });
    }

    private RunMigrations() {}

    /** The tag at {@link PersistedRun#SCHEMA_VERSION}, or an exception saying why it cannot be. */
    public static CompoundTag toCurrent(CompoundTag tag) {
        int version = tag.getInt("schema_version");
        if (version == PersistedRun.SCHEMA_VERSION) return tag;
        if (version > PersistedRun.SCHEMA_VERSION) {
            throw new IllegalArgumentException("run schema_version " + version + " comes from a newer build than this one"
                    + " (this build reads " + PersistedRun.SCHEMA_VERSION + "); refusing to read it with older rules");
        }
        if (version < OLDEST_SUPPORTED) {
            throw new IllegalArgumentException("run schema_version " + version + " is older than the oldest shape this"
                    + " build can migrate (" + OLDEST_SUPPORTED + ")");
        }

        CompoundTag migrated = tag;
        for (int from = version; from < PersistedRun.SCHEMA_VERSION; from++) {
            UnaryOperator<CompoundTag> step = STEPS.get(from);
            if (step == null) {
                throw new IllegalArgumentException("no migration step from run schema_version " + from + " to "
                        + (from + 1) + "; the chain to " + PersistedRun.SCHEMA_VERSION + " is incomplete");
            }
            migrated = step.apply(migrated);
            int reached = migrated.getInt("schema_version");
            if (reached != from + 1) {
                // A step that forgets to stamp the version would loop or silently stop early.
                throw new IllegalStateException("migration step " + from + "->" + (from + 1)
                        + " left schema_version at " + reached);
            }
        }
        return migrated;
    }

    /** True when {@link #toCurrent} has a complete path for {@code version}. */
    public static boolean canRead(int version) {
        if (version == PersistedRun.SCHEMA_VERSION) return true;
        if (version > PersistedRun.SCHEMA_VERSION || version < OLDEST_SUPPORTED) return false;
        for (int from = version; from < PersistedRun.SCHEMA_VERSION; from++) {
            if (!STEPS.containsKey(from)) return false;
        }
        return true;
    }
}
