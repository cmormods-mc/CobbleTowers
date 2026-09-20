package com.cobbletowers.persistence;

import com.cobbletowers.api.tower.RunState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * @param lastBankedFloor   the floor index through which the ledger has already been priced and
 *                          granted (TDS #24, #30); 0 means nothing has been banked yet
 * @param vendorPurchases   how many times this run has bought each vendor service (TDS #19); a
 *                          service absent from this map has never been bought
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
        RunModifierState modifiers,
        int lastBankedFloor,
        Map<ResourceLocation, Integer> vendorPurchases) {

    /**
     * The only shape this build writes or reads.
     *
     * <p>2 added the instance cell; 3 added the unclaimed ledger; 4 added the drafted modifiers; 5
     * added how much of the ledger has been banked; 6 added vendor purchase counts. Older files are
     * migrated forward by {@code RunMigrations}, which is what that framework was shipped empty for.
     */
    public static final int SCHEMA_VERSION = 6;

    private static List<LedgerEntry> readLedger(CompoundTag tag) {
        List<LedgerEntry> ledger = new ArrayList<>();
        ListTag stored = tag.getList("ledger", Tag.TAG_COMPOUND);
        for (int i = 0; i < stored.size(); i++) ledger.add(LedgerEntry.fromTag(stored.getCompound(i)));
        return ledger;
    }

    private static Map<ResourceLocation, Integer> readVendorPurchases(CompoundTag tag) {
        Map<ResourceLocation, Integer> purchases = new LinkedHashMap<>();
        ListTag stored = tag.getList("vendor_purchases", Tag.TAG_COMPOUND);
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag entry = stored.getCompound(i);
            ResourceLocation serviceId = ResourceLocation.tryParse(entry.getString("service"));
            if (serviceId != null) purchases.put(serviceId, entry.getInt("count"));
        }
        return purchases;
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
        vendorPurchases = Map.copyOf(vendorPurchases);
        if (floorIndex < 0) throw new IllegalArgumentException("floorIndex must be >= 0, got " + floorIndex);
        if (lastBankedFloor < 0) {
            throw new IllegalArgumentException("lastBankedFloor must be >= 0, got " + lastBankedFloor);
        }
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
        tag.putInt("last_banked_floor", lastBankedFloor);
        ListTag purchases = new ListTag();
        for (Map.Entry<ResourceLocation, Integer> entry : vendorPurchases.entrySet()) {
            CompoundTag purchase = new CompoundTag();
            purchase.putString("service", entry.getKey().toString());
            purchase.putInt("count", entry.getValue());
            purchases.add(purchase);
        }
        tag.put("vendor_purchases", purchases);
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
                        : RunModifierState.EMPTY,
                tag.getInt("last_banked_floor"),
                readVendorPurchases(tag));
    }

    /** The same run, recorded as it stands after {@code at}. */
    public PersistedRun touched(long at) {
        return new PersistedRun(runId, schemaVersion, towerId, towerRevision, towerDigest, rulesetRevision,
                structureRevision, seed, floorIndex, state, participants, lastCheckpoint, committedTransactions,
                at, cell, ledger, modifiers, lastBankedFloor, vendorPurchases);
    }

    /** The same run with one more thing earned. The pool only ever grows until it is banked. */
    public PersistedRun withEarned(LedgerEntry entry, long at) {
        List<LedgerEntry> next = new ArrayList<>(ledger);
        next.add(entry);
        return new PersistedRun(runId, schemaVersion, towerId, towerRevision, towerDigest, rulesetRevision,
                structureRevision, seed, floorIndex, state, participants, lastCheckpoint, committedTransactions,
                at, cell, next, modifiers, lastBankedFloor, vendorPurchases);
    }

    /** The same run holding {@code leased}, or holding none when it is empty. */
    public PersistedRun withCell(OptionalInt leased, long at) {
        return new PersistedRun(runId, schemaVersion, towerId, towerRevision, towerDigest, rulesetRevision,
                structureRevision, seed, floorIndex, state, participants, lastCheckpoint, committedTransactions,
                at, leased, ledger, modifiers, lastBankedFloor, vendorPurchases);
    }

    /** The same run carrying {@code next} as its drafted state. */
    public PersistedRun withModifiers(RunModifierState next, long at) {
        return new PersistedRun(runId, schemaVersion, towerId, towerRevision, towerDigest, rulesetRevision,
                structureRevision, seed, floorIndex, state, participants, lastCheckpoint, committedTransactions,
                at, cell, ledger, next, lastBankedFloor, vendorPurchases);
    }

    /** The same run with one more purchase of {@code serviceId} recorded (TDS #19). */
    public PersistedRun withVendorPurchase(ResourceLocation serviceId, long at) {
        Map<ResourceLocation, Integer> next = new LinkedHashMap<>(vendorPurchases);
        next.merge(serviceId, 1, Integer::sum);
        return new PersistedRun(runId, schemaVersion, towerId, towerRevision, towerDigest, rulesetRevision,
                structureRevision, seed, floorIndex, state, participants, lastCheckpoint, committedTransactions,
                at, cell, ledger, modifiers, lastBankedFloor, next);
    }

    /** How many times this run has already bought {@code serviceId} (TDS #19). */
    public int purchasesOf(ResourceLocation serviceId) {
        return vendorPurchases.getOrDefault(serviceId, 0);
    }

    /**
     * The same run with its ledger priced through {@code throughFloor} and {@code grantKey} committed.
     *
     * <p>{@code grantKey} is deliberately distinct from any transition's own checkpoint key: by the
     * time a grant runs, the run's state has already moved, so the state machine's own replay guard
     * (a move cannot be applied twice because applying it moves the state) cannot cover it. This is
     * the economic commit TDS #30 was left unused for -- its own key, checked before granting, so a
     * retry after a crash between the transition and the grant is a safe no-op rather than a second
     * payout.
     */
    public PersistedRun banked(int throughFloor, String grantKey, long at) {
        List<String> transactions = new ArrayList<>(committedTransactions);
        transactions.add(grantKey);
        return new PersistedRun(runId, schemaVersion, towerId, towerRevision, towerDigest, rulesetRevision,
                structureRevision, seed, floorIndex, state, participants, lastCheckpoint, transactions,
                at, cell, ledger, modifiers, throughFloor, vendorPurchases);
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
