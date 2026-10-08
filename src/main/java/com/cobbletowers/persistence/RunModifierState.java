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
 * Everything a run has drafted: carried, locked in and the open draft. One record because they only make sense
 * together. Ids, re-resolved on read (TDS section 10).
 * @param accumulated in draft order, one per copy held
 * @param lockedIn the subset made permanent by a Lock-In Draft (TDS #57); also in {@code accumulated}
 * @param draft the draft on the table, open or just resolved
 * @param relics relics found so far (P34); they sum into effects but are not challenges, so never count toward a
 *     Lock-In
 * @param accumulatedFloors the floor each {@code accumulated} entry first applied to, same order; shorter than the
 *     list (or 0) for an entry taken before this was recorded
 * @param relicFloors the same for {@code relics}
 */
public record RunModifierState(
        List<ResourceLocation> accumulated,
        List<ResourceLocation> lockedIn,
        Optional<PersistedDraft> draft,
        List<ResourceLocation> relics,
        List<Integer> accumulatedFloors,
        List<Integer> relicFloors) {

    /** Relics a run can hold at once; a milestone offers none past this. */
    public static final int MAX_RELICS = 6;

    /** A run that has drafted nothing. What every run created before P8 is read back as. */
    public static final RunModifierState EMPTY = new RunModifierState(List.of(), List.of(), Optional.empty(), List.of());

    public RunModifierState(List<ResourceLocation> accumulated, List<ResourceLocation> lockedIn,
                            Optional<PersistedDraft> draft) {
        this(accumulated, lockedIn, draft, List.of());
    }

    /** A state that does not know which floors things were taken on. */
    public RunModifierState(List<ResourceLocation> accumulated, List<ResourceLocation> lockedIn,
                            Optional<PersistedDraft> draft, List<ResourceLocation> relics) {
        this(accumulated, lockedIn, draft, relics, List.of(), List.of());
    }

    public RunModifierState {
        accumulated = List.copyOf(accumulated);
        lockedIn = List.copyOf(lockedIn);
        relics = List.copyOf(relics);
        accumulatedFloors = List.copyOf(accumulatedFloors);
        relicFloors = List.copyOf(relicFloors);
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
        return new RunModifierState(accumulated, lockedIn, Optional.of(opened), relics, accumulatedFloors, relicFloors);
    }

    /** The same state with the draft cleared away, e.g. when a run moves on to the next floor. */
    public RunModifierState withoutDraft() {
        return new RunModifierState(accumulated, lockedIn, Optional.empty(), relics, accumulatedFloors, relicFloors);
    }

    /** The same state having taken {@code modifier} (floor not recorded). */
    public RunModifierState accumulating(ResourceLocation modifier) {
        return accumulating(modifier, 0);
    }

    /** The same state having taken {@code modifier}, which first applies to {@code floor}. */
    public RunModifierState accumulating(ResourceLocation modifier, int floor) {
        List<ResourceLocation> next = new ArrayList<>(accumulated);
        List<Integer> floors = padded(accumulatedFloors, accumulated.size());
        next.add(modifier);
        floors.add(floor);
        return new RunModifierState(next, lockedIn, draft, relics, floors, relicFloors);
    }

    /** The floor the {@code index}th held modifier first applied to, 0 when it was not recorded. */
    public int modifierFloor(int index) {
        return index >= 0 && index < accumulatedFloors.size() ? accumulatedFloors.get(index) : 0;
    }

    /** The floor the {@code index}th relic first applied to, 0 when it was not recorded. */
    public int relicFloor(int index) {
        return index >= 0 && index < relicFloors.size() ? relicFloors.get(index) : 0;
    }

    private static List<Integer> padded(List<Integer> floors, int size) {
        List<Integer> out = new ArrayList<>(floors);
        while (out.size() < size) out.add(0);
        return out;
    }

    /** The same state with {@code modifier} made permanent; locking in an already locked-in modifier is a no-op. */
    public RunModifierState lockingIn(ResourceLocation modifier) {
        if (lockedIn.contains(modifier)) return this;
        List<ResourceLocation> next = new ArrayList<>(lockedIn);
        next.add(modifier);
        return new RunModifierState(accumulated, next, draft, relics, accumulatedFloors, relicFloors);
    }

    /** The same state having found {@code relic} (floor not recorded). */
    public RunModifierState withRelic(ResourceLocation relic) {
        return withRelic(relic, 0);
    }

    /** The same state having found {@code relic}, which first applies to {@code floor}. */
    public RunModifierState withRelic(ResourceLocation relic, int floor) {
        List<ResourceLocation> next = new ArrayList<>(relics);
        List<Integer> floors = padded(relicFloors, relics.size());
        next.add(relic);
        floors.add(floor);
        return new RunModifierState(accumulated, lockedIn, draft, next, accumulatedFloors, floors);
    }

    /** The same state having lost {@code relic} (a lost gamble, P34b). */
    public RunModifierState withoutRelic(ResourceLocation relic) {
        List<ResourceLocation> next = new ArrayList<>(relics);
        List<Integer> floors = padded(relicFloors, relics.size());
        int at = next.indexOf(relic);
        if (at >= 0) {
            next.remove(at);
            floors.remove(at);
        }
        return new RunModifierState(accumulated, lockedIn, draft, next, accumulatedFloors, floors);
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
        tag.put("accumulated_floors", new net.minecraft.nbt.IntArrayTag(accumulatedFloors.stream().mapToInt(Integer::intValue).toArray()));
        tag.put("relic_floors", new net.minecraft.nbt.IntArrayTag(relicFloors.stream().mapToInt(Integer::intValue).toArray()));
        return tag;
    }

    public static RunModifierState fromTag(CompoundTag tag) {
        return new RunModifierState(
                readIds(tag, "accumulated"),
                readIds(tag, "locked_in"),
                tag.contains("draft", Tag.TAG_COMPOUND)
                        ? Optional.of(PersistedDraft.fromTag(tag.getCompound("draft")))
                        : Optional.empty(),
                readIds(tag, "relics"),
                readFloors(tag, "accumulated_floors"),
                readFloors(tag, "relic_floors"));
    }

    /** Absent in a run saved before floors were recorded: nothing is known, which reads as 0. */
    private static List<Integer> readFloors(CompoundTag tag, String key) {
        List<Integer> floors = new ArrayList<>();
        for (int floor : tag.getIntArray(key)) floors.add(floor);
        return floors;
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
