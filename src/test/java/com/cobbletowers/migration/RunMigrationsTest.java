package com.cobbletowers.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.persistence.PersistedRun;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Only one shape has ever existed, so these are mostly about what happens to the shapes that do not.
 */
class RunMigrationsTest {

    private static final UUID RUN = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");

    @Test
    @DisplayName("a current run passes through untouched")
    void currentIsLeftAlone() {
        CompoundTag tag = TestRuns.fresh(RUN).toTag();

        assertSame(tag, RunMigrations.toCurrent(tag));
        assertEquals(TestRuns.fresh(RUN), PersistedRun.fromTag(RunMigrations.toCurrent(tag)));
    }

    @Test
    @DisplayName("a run from a newer build is refused, not read with older rules")
    void futureIsRefused() {
        CompoundTag tag = TestRuns.fresh(RUN).toTag();
        tag.putInt("schema_version", PersistedRun.SCHEMA_VERSION + 1);

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> RunMigrations.toCurrent(tag));

        assertTrue(thrown.getMessage().contains("newer build"), thrown.getMessage());
        assertFalse(RunMigrations.canRead(PersistedRun.SCHEMA_VERSION + 1));
    }

    @Test
    @DisplayName("a version older than the chain is refused rather than guessed at")
    void tooOldIsRefused() {
        CompoundTag tag = TestRuns.fresh(RUN).toTag();
        tag.putInt("schema_version", RunMigrations.OLDEST_SUPPORTED - 1);

        assertThrows(IllegalArgumentException.class, () -> RunMigrations.toCurrent(tag));
        assertFalse(RunMigrations.canRead(RunMigrations.OLDEST_SUPPORTED - 1));
    }

    @Test
    @DisplayName("every version the build claims to support has a complete path to the current one")
    void theChainHasNoGaps() {
        // The invariant that matters when version 2 arrives: claiming to read a version and having
        // no step for it is the failure this catches, on the day the step is forgotten rather than
        // on the day a player's run will not load.
        for (int version = RunMigrations.OLDEST_SUPPORTED; version <= PersistedRun.SCHEMA_VERSION; version++) {
            assertTrue(RunMigrations.canRead(version), "no migration path from schema_version " + version);
        }
    }
}
