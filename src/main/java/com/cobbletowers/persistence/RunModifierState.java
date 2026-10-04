package com.cobbletowers.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything a run has drafted: what it carries, what it has locked in, and the draft it is sitting
 * at right now.
 *
 * <p>One record rather than three fields on {@link PersistedRun}, for the reason {@link
 * RunCheckpoint} gives about its own two: these change together and only ever make sense together.
 * A run that accumulated a modifier without closing the draft that awarded it is not a state worth
 * being able to write.
 *
 * <p>Ids, never definitions (TDS §10). They are re-resolved against loaded content on read.
 *
 * @param accumulated in draft order, one entry per copy held -- a modifier taken twice appears twice
 * @param lockedIn    the subset made permanent by a Lock-In Draft (TDS #57); each also appears in
 *                    {@code accumulated}, because locking a modifier in does not stop it being held
 * @param draft       the draft on the table, open or just resolved
 * @param relics      the relics found so far (P34), in the order found; they sum into the run's effects like
 *                    modifiers but are not challenges, so they never count toward a Lock-In
 */
public record RunModifierState(
        List<ResourceLocation> accumulated,
        List<ResourceLocation> lockedIn,
        Optional<PersistedDraft> draft,
        List<ResourceLocation> relics) {

    /** Relics a run can hold at once; a milestone offers none past this. */
    public static final int MAX_RELICS = 6;

    /** A run that has drafted nothing. What every run created before P8 is read back as. */
    public static final RunModifierState EMPTY = new RunModifierState(List.of(), List.of(), Optional.empty(), List.of());

    public RunModifierState(List<ResourceLocation> accumulated, List<ResourceLocation> lockedIn,
                            Optional<PersistedDraft> draft) {
        this(accumulated, lockedIn, draft, List.of());
    }

    public RunModifierState {
        accumulated = List.copyOf(accumulated);
        lockedIn = List.copyOf(lockedIn);
        relics = List.copyOf(relics);
        Objects.requireNonNull(draft, "draft");
        for (ResourceLocation locked : lockedIn) {
            if (!accumulated.contains(locked)) {
                throw new IllegalArgumentException("locked-in modifier " + locked + " is not accumulated");
            }
        }
    }

    /** How many challenges the run has taken: the number TDS #57 counts to five on. */
    public int challengeCount() {
        return accumulated.size();
    }

    /** Whether a draft is on the table and still unanswered. */
    public boolean hasOpenDraft() {
        return draft.isPresent() && !draft.get().resolved();
    }

    public RunModifierState withDraft(PersistedDraft opened) {
        return new RunModifierState(accumulated, lockedIn, Optional.of(opened), relics);
    }

    /** The same state with the draft cleared away, e.g. when a run moves on to the next floor. */
    public RunModifierState withoutDraft() {
        return new RunModifierState(accumulated, lockedIn, Optional.empty(), relics);
    }

    /** The same state having taken {@code modifier}. */
    public RunModifierState accumulating(ResourceLocation modifier) {
        List<ResourceLocation> next = new ArrayList<>(accumulated);
        next.add(modifier);
        return new RunModifierState(next, lockedIn, draft, relics);
    }

    /**
     * The same state with {@code modifier} made permanent.
     *
     * <p>Locking in something already locked in is a no-op rather than an error: the draft that
     * offers them draws from what the run holds, and a second lock-in on the same modifier is a
     * reachable, harmless outcome that should not fail a floor.
     */
    public RunModifierState lockingIn(ResourceLocation modifier) {
        if (lockedIn.contains(modifier)) return this;
        List<ResourceLocation> next = new ArrayList<>(lockedIn);
        next.add(modifier);
        return new RunModifierState(accumulated, next, draft, relics);
    }

    /** The same state having found {@code relic}. */
    public RunModifierState withRelic(ResourceLocation relic) {
        List<ResourceLocation> next = new ArrayList<>(relics);
        next.add(relic);
        return new RunModifierState(accumulated, lockedIn, draft, next);
    }

    /** Whether there is room for another relic. */
    public boolean hasRelicRoom() {
        return relics.size() < MAX_RELICS;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("accumulated", idList(accumulated));
        tag.put("locked_in", idList(lockedIn));
        draft.ifPresent(open -> tag.put("draft", open.toTag()));
        tag.put("relics", idList(relics));
        return tag;
    }

    public static RunModifierState fromTag(CompoundTag tag) {
        return new RunModifierState(
                readIds(tag, "accumulated"),
                readIds(tag, "locked_in"),
                tag.contains("draft", Tag.TAG_COMPOUND)
                        ? Optional.of(PersistedDraft.fromTag(tag.getCompound("draft")))
                        : Optional.empty(),
                readIds(tag, "relics"));
    }

    private static ListTag idList(List<ResourceLocation> ids) {
        ListTag list = new ListTag();
        for (ResourceLocation id : ids) list.add(StringTag.valueOf(id.toString()));
        return list;
    }

    private static List<ResourceLocation> readIds(CompoundTag tag, String key) {
        List<ResourceLocation> ids = new ArrayList<>();
        ListTag stored = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < stored.size(); i++) {
            String raw = stored.getString(i);
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) throw new IllegalArgumentException("run holds an invalid modifier id: '" + raw + "'");
            ids.add(id);
        }
        return ids;
    }
}
