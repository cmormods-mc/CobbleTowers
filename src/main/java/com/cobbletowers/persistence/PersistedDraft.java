package com.cobbletowers.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * A draft as it is written to disk: the cards offered, who voted for what, and what won.
 *
 * <p>Identifiers only, never a definition object (TDS §10). The cards are modifier ids re-resolved
 * against whatever content is loaded when the run is read back, which is the same rule the rest of
 * the run follows -- and the digest the run pinned at creation is what makes an edit to that content
 * visible rather than silent (TDS #40).
 *
 * <p><b>The cards are stored even though they are derivable.</b> Everything else deterministic is
 * re-derived rather than saved, so this deserves its reason: the draw filters the pool by what the
 * run is <i>eligible</i> for, and eligibility depends on what it has already drafted. Re-deriving
 * would be correct only as long as nothing about the accumulation ever changed shape. Storing the
 * offer costs three strings and removes that dependency entirely.
 */
public record PersistedDraft(
        int floorIndex,
        boolean lockIn,
        List<ResourceLocation> cards,
        Map<UUID, Integer> votes,
        OptionalInt chosen,
        boolean decidedByTieBreak) {

    public PersistedDraft {
        cards = List.copyOf(cards);
        votes = Map.copyOf(votes);
        Objects.requireNonNull(chosen, "chosen");
        if (floorIndex < 1) throw new IllegalArgumentException("floorIndex must be >= 1, got " + floorIndex);
        if (cards.isEmpty()) throw new IllegalArgumentException("a draft must offer at least one card");
        if (chosen.isPresent() && (chosen.getAsInt() < 0 || chosen.getAsInt() >= cards.size())) {
            throw new IllegalArgumentException("chosen card " + chosen.getAsInt() + " is not one of the "
                    + cards.size() + " offered");
        }
    }

    /** A fresh draft: cards on the table, nobody has voted. */
    public static PersistedDraft opening(int floorIndex, boolean lockIn, List<ResourceLocation> cards) {
        return new PersistedDraft(floorIndex, lockIn, cards, Map.of(), OptionalInt.empty(), false);
    }

    public boolean resolved() {
        return chosen.isPresent();
    }

    /**
     * The same draft with one player's vote recorded.
     *
     * <p>A player who votes twice replaces their own vote rather than adding one -- the map is keyed
     * by player for exactly that reason.
     */
    public PersistedDraft withVote(UUID playerId, int cardIndex) {
        if (cardIndex < 0 || cardIndex >= cards.size()) {
            throw new IllegalArgumentException("card " + cardIndex + " is not one of the " + cards.size()
                    + " offered");
        }
        Map<UUID, Integer> updated = new LinkedHashMap<>(votes);
        updated.put(playerId, cardIndex);
        return new PersistedDraft(floorIndex, lockIn, cards, updated, chosen, decidedByTieBreak);
    }

    /** The same draft, settled. The cards stay, so what was turned down can still be read. */
    public PersistedDraft resolvedAs(int cardIndex, boolean byTieBreak) {
        return new PersistedDraft(floorIndex, lockIn, cards, votes, OptionalInt.of(cardIndex), byTieBreak);
    }

    /** The winning card's modifier id, once there is one. */
    public Optional<ResourceLocation> chosenModifier() {
        return chosen.isPresent() ? Optional.of(cards.get(chosen.getAsInt())) : Optional.empty();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("floor", floorIndex);
        tag.putBoolean("lock_in", lockIn);
        ListTag offered = new ListTag();
        for (ResourceLocation card : cards) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", card.toString());
            offered.add(entry);
        }
        tag.put("cards", offered);
        ListTag cast = new ListTag();
        for (Map.Entry<UUID, Integer> vote : votes.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", vote.getKey());
            entry.putInt("card", vote.getValue());
            cast.add(entry);
        }
        tag.put("votes", cast);
        chosen.ifPresent(index -> tag.putInt("chosen", index));
        tag.putBoolean("tie_break", decidedByTieBreak);
        return tag;
    }

    public static PersistedDraft fromTag(CompoundTag tag) {
        List<ResourceLocation> cards = new ArrayList<>();
        ListTag offered = tag.getList("cards", Tag.TAG_COMPOUND);
        for (int i = 0; i < offered.size(); i++) {
            String raw = offered.getCompound(i).getString("id");
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) throw new IllegalArgumentException("draft holds an invalid modifier id: '" + raw + "'");
            cards.add(id);
        }
        Map<UUID, Integer> votes = new LinkedHashMap<>();
        ListTag cast = tag.getList("votes", Tag.TAG_COMPOUND);
        for (int i = 0; i < cast.size(); i++) {
            CompoundTag entry = cast.getCompound(i);
            votes.put(entry.getUUID("player"), entry.getInt("card"));
        }
        return new PersistedDraft(
                tag.getInt("floor"),
                tag.getBoolean("lock_in"),
                cards,
                votes,
                tag.contains("chosen", Tag.TAG_INT) ? OptionalInt.of(tag.getInt("chosen")) : OptionalInt.empty(),
                tag.getBoolean("tie_break"));
    }
}
