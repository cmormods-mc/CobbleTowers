package com.cobbletowers.persistence;

import com.cobbletowers.api.tower.RunState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * A run as it is written to disk: identifiers, revisions, a seed and logical state.
 *
 * <p>P1 defines the shape and proves it round-trips; P2 attaches it to a world. Nothing here stores a
 * live entity, a battle or a player object (TDS §10) -- a restart rebuilds those from the seed and the
 * pinned revisions.
 *
 * @param towerRevision     the author's revision of the tower when the run started
 * @param towerDigest       the content digest then, so an edit is distinguishable from a renumber
 * @param structureRevision the tower structure's revision, versioned independently (TDS #40)
 * @param lastCheckpoint    where the run was last committed, or empty before its first checkpoint
 * @param committedTransactions keys of mutations already applied, so replaying is safe (TDS #30)
 * @param updatedAt         epoch millis of the last write, used to retire finished runs
 * @param cell              the instance cell leased to this run, or empty before one is allocated
 * @param ledger            what the run has earned so far, with no worth attached to it yet
 * @param modifiers         what the run has drafted, and the draft it is sitting at (TDS #2)
 */
public record PersistedRun(
        UUID runId,
        int schemaVersion,
        ResourceLocation towerId,
        int towerRevision,
        String towerDigest,
        int rulesetRevision,
        int structureRevision,
        long seed,
        int floorIndex,
        RunState state,
        List<PersistedParticipant> participants,
        Optional<RunCheckpoint> lastCheckpoint,
        List<String> committedTransactions,
        long updatedAt,
        OptionalInt cell,
        List<LedgerEntry> ledger,
        RunModifierState modifiers) {

    /**
     * The only shape this build writes or reads.
     *
     * <p>2 added the instance cell; 3 added the unclaimed ledger; 4 added the drafted modifiers.
     * Older files are migrated forward by {@code RunMigrations}, which is what that framework was
     * shipped empty for.
     */
    public static final int SCHEMA_VERSION = 4;

    private static List<LedgerEntry> readLedger(CompoundTag tag) {
        List<LedgerEntry> ledger = new ArrayList<>();
        ListTag stored = tag.getList("ledger", Tag.TAG_COMPOUND);
        for (int i = 0; i < stored.size(); i++) ledger.add(LedgerEntry.fromTag(stored.getCompound(i)));
        return ledger;
    }

    public PersistedRun {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(towerId, "towerId");
        Objects.requireNonNull(towerDigest, "towerDigest");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(lastCheckpoint, "lastCheckpoint");
        Objects.requireNonNull(modifiers, "modifiers");
        participants = List.copyOf(participants);
        committedTransactions = List.copyOf(committedTransactions);
        ledger = List.copyOf(ledger);
        if (floorIndex < 0) throw new IllegalArgumentException("floorIndex must be >= 0, got " + floorIndex);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", schemaVersion);
        tag.putUUID("run", runId);
        tag.putString("tower", towerId.toString());
        tag.putInt("tower_revision", towerRevision);
        tag.putString("tower_digest", towerDigest);
        tag.putInt("ruleset_revision", rulesetRevision);
        tag.putInt("structure_revision", structureRevision);
        tag.putLong("seed", seed);
        tag.putInt("floor", floorIndex);
        tag.putString("state", state.name().toLowerCase(Locale.ROOT));
        lastCheckpoint.ifPresent(checkpoint -> tag.put("last_checkpoint", checkpoint.toTag()));
        tag.putLong("updated_at", updatedAt);
        cell.ifPresent(index -> tag.putInt("cell", index));
        ListTag earned = new ListTag();
        for (LedgerEntry entry : ledger) earned.add(entry.toTag());
        tag.put("ledger", earned);
        tag.put("modifiers", modifiers.toTag());
        ListTag people = new ListTag();
        for (PersistedParticipant participant : participants) people.add(participant.toTag());
        tag.put("participants", people);
        ListTag transactions = new ListTag();
        for (String key : committedTransactions) transactions.add(StringTag.valueOf(key));
        tag.put("committed_transactions", transactions);
        return tag;
    }

    public static PersistedRun fromTag(CompoundTag tag) {
        int schemaVersion = tag.getInt("schema_version");
        if (schemaVersion != SCHEMA_VERSION) {
            // Refused, never guessed: a future schema read with today's rules would put a run back
            // together wrongly and then save it that way.
            throw new IllegalArgumentException("run schema_version " + schemaVersion
                    + " is not supported by this build; expected " + SCHEMA_VERSION);
        }
        ResourceLocation towerId = ResourceLocation.tryParse(tag.getString("tower"));
        if (towerId == null) throw new IllegalArgumentException("run names an invalid tower id: '" + tag.getString("tower") + "'");

        List<PersistedParticipant> participants = new ArrayList<>();
        ListTag people = tag.getList("participants", Tag.TAG_COMPOUND);
        for (int i = 0; i < people.size(); i++) participants.add(PersistedParticipant.fromTag(people.getCompound(i)));

        List<String> transactions = new ArrayList<>();
        ListTag stored = tag.getList("committed_transactions", Tag.TAG_STRING);
        for (int i = 0; i < stored.size(); i++) transactions.add(stored.getString(i));

        return new PersistedRun(
                tag.getUUID("run"),
                schemaVersion,
                towerId,
                tag.getInt("tower_revision"),
                tag.getString("tower_digest"),
                tag.getInt("ruleset_revision"),
                tag.getInt("structure_revision"),
                tag.getLong("seed"),
                tag.getInt("floor"),
                parseState(tag.getString("state")),
                participants,
                tag.contains("last_checkpoint", Tag.TAG_COMPOUND)
                        ? Optional.of(RunCheckpoint.fromTag(tag.getCompound("last_checkpoint")))
                        : Optional.empty(),
                transactions,
                tag.getLong("updated_at"),
                // Absent means no cell, which is also exactly what a version 1 run says.
                tag.contains("cell", Tag.TAG_INT) ? OptionalInt.of(tag.getInt("cell")) : OptionalInt.empty(),
                readLedger(tag),
                tag.contains("modifiers", Tag.TAG_COMPOUND)
                        ? RunModifierState.fromTag(tag.getCompound("modifiers"))
                        : RunModifierState.EMPTY);
    }

    /** The same run, recorded as it stands after {@code at}. */
    public PersistedRun touched(long at) {
        return new PersistedRun(runId, schemaVersion, towerId, towerRevision, towerDigest, rulesetRevision,
                structureRevision, seed, floorIndex, state, participants, lastCheckpoint, committedTransactions,
                at, cell, ledger, modifiers);
    }

    /** The same run with one more thing earned. The pool only ever grows until it is banked. */
    public PersistedRun withEarned(LedgerEntry entry, long at) {
        List<LedgerEntry> next = new ArrayList<>(ledger);
        next.add(entry);
        return new PersistedRun(runId, schemaVersion, towerId, towerRevision, towerDigest, rulesetRevision,
                structureRevision, seed, floorIndex, state, participants, lastCheckpoint, committedTransactions,
                at, cell, next, modifiers);
    }

    /** The same run holding {@code leased}, or holding none when it is empty. */
    public PersistedRun withCell(OptionalInt leased, long at) {
        return new PersistedRun(runId, schemaVersion, towerId, towerRevision, towerDigest, rulesetRevision,
                structureRevision, seed, floorIndex, state, participants, lastCheckpoint, committedTransactions,
                at, leased, ledger, modifiers);
    }

    /** The same run carrying {@code next} as its drafted state. */
    public PersistedRun withModifiers(RunModifierState next, long at) {
        return new PersistedRun(runId, schemaVersion, towerId, towerRevision, towerDigest, rulesetRevision,
                structureRevision, seed, floorIndex, state, participants, lastCheckpoint, committedTransactions,
                at, cell, ledger, next);
    }

    /** True when this key has already been committed, so applying the move again must not repeat it. */
    public boolean hasCommitted(String key) {
        return committedTransactions.contains(key);
    }

    /** True when the run is finished and only kept for history. */
    public boolean isRetired() {
        return state.isTerminal();
    }

    /** True when {@code digest} differs from what this run pinned, i.e. the tower was edited since. */
    public boolean contentChangedFrom(String digest) {
        return !towerDigest.equals(digest);
    }

    private static RunState parseState(String raw) {
        try {
            return RunState.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("run has unknown state '" + raw + "'");
        }
    }
}
