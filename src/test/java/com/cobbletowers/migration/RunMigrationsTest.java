package com.cobbletowers.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.RunModifierState;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The chain that lets a run written by an older build still load. */
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
    @DisplayName("a version 1 run loads, and comes back holding no cell")
    void versionOneIsMigrated() {
        // A real v1 tag: what P2 wrote, before the instance cell existed.
        CompoundTag v1 = TestRuns.fresh(RUN).toTag();
        v1.putInt("schema_version", 1);
        v1.remove("cell");

        CompoundTag migrated = RunMigrations.toCurrent(v1);

        assertEquals(PersistedRun.SCHEMA_VERSION, migrated.getInt("schema_version"));
        PersistedRun run = PersistedRun.fromTag(migrated);
        assertEquals(OptionalInt.empty(), run.cell(),
                "a run written before cells existed cannot have held one");
        assertEquals(RUN, run.runId(), "and everything else survives the step untouched");
        assertTrue(RunMigrations.canRead(1));
    }

    @Test
    @DisplayName("a version 2 run loads, and comes back with an empty pool")
    void versionTwoIsMigrated() {
        // What P4 wrote, before the ledger existed. This step carries data rather than only stamping
        // a version, which is the first time the chain has had to do that.
        CompoundTag v2 = TestRuns.fresh(RUN).toTag();
        v2.putInt("schema_version", 2);
        v2.remove("ledger");

        PersistedRun run = PersistedRun.fromTag(RunMigrations.toCurrent(v2));

        assertEquals(List.of(), run.ledger(), "a run that predates the pool earned nothing into it");
        assertEquals(RUN, run.runId());
        assertTrue(RunMigrations.canRead(2));
    }

    @Test
    @DisplayName("a version 3 run loads, and comes back having drafted nothing")
    void versionThreeLoads() {
        CompoundTag v3 = TestRuns.fresh(RUN).toTag();
        v3.putInt("schema_version", 3);
        v3.remove("modifiers");

        PersistedRun run = PersistedRun.fromTag(RunMigrations.toCurrent(v3));

        assertEquals(RunModifierState.EMPTY, run.modifiers(),
                "a run that predates the draft drafted nothing, which is the honest answer");
        assertEquals(RUN, run.runId());
        assertTrue(RunMigrations.canRead(3));
    }

    @Test
    @DisplayName("a migrated run has the same shape on disk as a freshly written one")
    void migrationMatchesAFreshWrite() {
        // A migration whose output differs from a fresh write is a difference that surfaces later,
        // somewhere less obvious than here.
        CompoundTag v3 = TestRuns.fresh(RUN).toTag();
        v3.putInt("schema_version", 3);
        v3.remove("modifiers");

        assertEquals(TestRuns.fresh(RUN).toTag(), RunMigrations.toCurrent(v3));
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
