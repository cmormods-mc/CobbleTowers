package com.cobbletowers.migration;

import com.cobbletowers.persistence.PersistedRun;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;

/**
 * Moves a stored run forward to the shape this build reads (TDS #40).
 *
 * <p>{@link PersistedRun#fromTag} deliberately understands exactly one shape. Everything about
 * versions lives here instead, so the reader stays simple and each migration is one entry that can
 * be read, tested and argued about on its own.
 *
 * <p>Three answers, and only three:
 * <ul>
 *   <li>the current version: returned untouched;</li>
 *   <li>a known older version: each step applied in order until it is current;</li>
 *   <li>anything else -- a version from a newer build, or one so old no step remains -- is
 *       <b>refused</b>. Reading an unknown shape with today's rules would put the run back together
 *       wrongly and then save it that way, which is worse than declining to load it.</li>
 * </ul>
 *
 * <p>Only version 1 has ever existed, so {@link #STEPS} is empty. That is the point of shipping it
 * now: adding version 2 is one entry and one test, not a reader rewrite.
 */
public final class RunMigrations {

    /** The oldest shape a step chain still exists for. */
    public static final int OLDEST_SUPPORTED = 1;

    /**
     * A step keyed by the version it reads: {@code STEPS.get(n)} turns a version {@code n} tag into
     * a version {@code n + 1} tag, and must set {@code schema_version} itself.
     */
    private static final Map<Integer, UnaryOperator<CompoundTag>> STEPS = new LinkedHashMap<>();

    static {
        // No entries yet. When version 2 arrives:
        //   STEPS.put(1, tag -> { ...rewrite...; tag.putInt("schema_version", 2); return tag; });
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
