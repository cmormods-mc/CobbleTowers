package com.cobbletowers.persistence;

import com.cobbletowers.api.tower.RunState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
 * @param lastCheckpoint    the idempotency key of the last committed checkpoint, or empty
 * @param committedTransactions keys of economic mutations already applied, so replaying is safe (TDS #30)
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
        String lastCheckpoint,
        List<String> committedTransactions) {

    /** The only shape this build writes or reads. */
    public static final int SCHEMA_VERSION = 1;

    public PersistedRun {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(towerId, "towerId");
        Objects.requireNonNull(towerDigest, "towerDigest");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(lastCheckpoint, "lastCheckpoint");
        participants = List.copyOf(participants);
        committedTransactions = List.copyOf(committedTransactions);
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
        tag.putString("last_checkpoint", lastCheckpoint);
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
                tag.getString("last_checkpoint"),
                transactions);
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
