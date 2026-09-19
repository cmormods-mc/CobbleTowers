package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.api.tower.participant.CombatState;
import com.cobbletowers.api.tower.participant.ConnectionState;
import com.cobbletowers.api.tower.participant.MembershipState;
import com.cobbletowers.api.tower.participant.ParticipantState;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersistedRunTest {

    private static final ResourceLocation TOWER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral");

    private static PersistedRun run(RunState state, ParticipantState participantState) {
        PersistedParticipant participant = new PersistedParticipant(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), participantState,
                List.of(UUID.fromString("22222222-2222-2222-2222-222222222222")));
        return new PersistedRun(UUID.fromString("33333333-3333-3333-3333-333333333333"),
                PersistedRun.SCHEMA_VERSION, TOWER, 4, "abc123", 2, 7, -9876543210L, 3, state,
                List.of(participant),
                Optional.of(new RunCheckpoint("run:x:floor:3:ready", RunState.FLOOR_READY)),
                List.of("run:x:floor:2:banked"), 1_726_000_000_000L, OptionalInt.of(12),
                List.of(LedgerEntry.opponentDefeated(3, TOWER, UUID.fromString("44444444-4444-4444-4444-444444444444"), 7L)),
                // A drafted, locked-in modifier and an open draft with a vote in it, so the round
                // trip covers the shape a run actually has mid-intermission rather than an empty one.
                new RunModifierState(
                        List.of(TOWER, TOWER),
                        List.of(TOWER),
                        Optional.of(PersistedDraft.opening(3, true, List.of(TOWER))
                                .withVote(UUID.fromString("11111111-1111-1111-1111-111111111111"), 0))));
    }

    @Test
    @DisplayName("a run survives a write and read for every state and every participant state")
    void roundTrip() {
        for (RunState state : RunState.values()) {
            for (ConnectionState connection : ConnectionState.values()) {
                for (CombatState combat : CombatState.values()) {
                    for (MembershipState membership : MembershipState.values()) {
                        PersistedRun original = run(state, new ParticipantState(connection, combat, membership));
                        assertEquals(original, PersistedRun.fromTag(original.toTag()),
                                state + " / " + connection + " / " + combat + " / " + membership);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the checkpoint and the clock reading survive a round trip, and absence stays absent")
    void checkpointRoundTrip() {
        PersistedRun withCheckpoint = run(RunState.FLOOR_READY, ParticipantState.joined());
        PersistedRun restored = PersistedRun.fromTag(withCheckpoint.toTag());
        assertEquals(Optional.of(new RunCheckpoint("run:x:floor:3:ready", RunState.FLOOR_READY)),
                restored.lastCheckpoint());
        assertEquals(1_726_000_000_000L, restored.updatedAt());
        assertEquals(OptionalInt.of(12), restored.cell(), "the instance lease is persisted with the run");

        // A run that has never checkpointed must come back with none, not with an empty-keyed one --
        // RunCheckpoint refuses a blank key precisely so that cannot be written in the first place.
        PersistedRun fresh = new PersistedRun(withCheckpoint.runId(), PersistedRun.SCHEMA_VERSION, TOWER, 4,
                "abc123", 2, 7, 1L, 1, RunState.CREATED, List.of(), Optional.empty(), List.of(), 5L,
                OptionalInt.empty(), List.of(), RunModifierState.EMPTY);
        assertEquals(Optional.empty(), PersistedRun.fromTag(fresh.toTag()).lastCheckpoint());
    }

    @Test
    @DisplayName("the unclaimed pool survives a round trip, entry by entry")
    void ledgerRoundTrip() {
        PersistedRun run = run(RunState.INTERMISSION, ParticipantState.joined());
        PersistedRun restored = PersistedRun.fromTag(run.toTag());

        assertEquals(run.ledger(), restored.ledger());
        assertEquals(LedgerEntry.Kind.OPPONENT_DEFEATED, restored.ledger().get(0).kind());
        assertEquals(3, restored.ledger().get(0).floorIndex());
    }

    @Test
    @DisplayName("earning appends and never rewrites what is already in the pool")
    void earningAppends() {
        // The pool only grows until it is banked. An entry that could be overwritten by the next one
        // would quietly shrink what a player is owed.
        PersistedRun run = run(RunState.ENCOUNTER_ACTIVE, ParticipantState.joined());
        int before = run.ledger().size();

        PersistedRun after = run
                .withEarned(LedgerEntry.opponentDefeated(4, TOWER, UUID.randomUUID(), 10L), 10L)
                .withEarned(LedgerEntry.floorCleared(4, TOWER, 11L), 11L);

        assertEquals(before + 2, after.ledger().size());
        assertEquals(run.ledger().get(0), after.ledger().get(0), "the first entry is untouched");
        assertEquals(LedgerEntry.Kind.FLOOR_CLEARED, after.ledger().get(after.ledger().size() - 1).kind());
        assertEquals(before, run.ledger().size(), "and the original record is unchanged");
    }

    @Test
    @DisplayName("a cleared floor belongs to the run, not to one player")
    void floorClearedHasNoPlayer() {
        LedgerEntry entry = LedgerEntry.floorCleared(2, TOWER, 1L);

        assertNull(entry.byPlayer());
        assertEquals(entry, LedgerEntry.fromTag(entry.toTag()), "and that survives the round trip");
    }

    @Test
    @DisplayName("a ledger entry of an unknown kind is refused rather than filed under the wrong one")
    void unknownLedgerKind() {
        CompoundTag tag = LedgerEntry.opponentDefeated(1, TOWER, UUID.randomUUID(), 1L).toTag();
        tag.putString("kind", "mystery_prize");

        assertThrows(IllegalArgumentException.class, () -> LedgerEntry.fromTag(tag));
    }

    @Test
    @DisplayName("a run from a newer build is refused, not read wrongly")
    void futureSchemaIsRefused() {
        CompoundTag tag = run(RunState.PREPARING, ParticipantState.joined()).toTag();
        tag.putInt("schema_version", PersistedRun.SCHEMA_VERSION + 1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> PersistedRun.fromTag(tag));
        assertTrue(thrown.getMessage().contains("schema_version"), thrown.getMessage());
    }

    @Test
    @DisplayName("an unknown participant state is refused rather than defaulted")
    void unknownParticipantState() {
        CompoundTag tag = run(RunState.PREPARING, ParticipantState.joined()).toTag();
        tag.getList("participants", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .putString("combat", "brainwashed");

        assertThrows(IllegalArgumentException.class, () -> PersistedRun.fromTag(tag));
    }

    @Test
    @DisplayName("the pinned digest says whether the tower was edited since the run started")
    void digestPinning() {
        PersistedRun original = run(RunState.INTERMISSION, ParticipantState.joined());

        assertFalse(original.contentChangedFrom("abc123"));
        assertTrue(original.contentChangedFrom("def456"));
    }

    @Test
    @DisplayName("identifiers and seeds survive exactly, negative seeds included")
    void identifiersSurvive() {
        PersistedRun original = run(RunState.ENCOUNTER_ACTIVE, ParticipantState.joined().knockedOut().disconnected());
        PersistedRun restored = PersistedRun.fromTag(original.toTag());

        assertEquals(original.runId(), restored.runId());
        assertEquals(original.seed(), restored.seed());
        assertEquals(TOWER, restored.towerId());
        assertEquals(List.of("run:x:floor:2:banked"), restored.committedTransactions());
        assertEquals(ConnectionState.DISCONNECTED, restored.participants().get(0).state().connection());
        assertEquals(CombatState.KNOCKED_OUT, restored.participants().get(0).state().combat());
    }
}
