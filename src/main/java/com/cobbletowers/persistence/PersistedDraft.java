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
 * A draft as written to disk: cards offered, votes and what won. Ids only (TDS section 10), re-resolved against
 * loaded content; the run's pinned digest makes edits visible (TDS #40). The cards are stored although derivable,
 * because eligibility depends on what the run already drafted.
 */
public record PersistedDraft(
        int floorIndex,
        boolean lockIn,
        List<ResourceLocation> cards,
        Map<UUID, Integer> votes,
        OptionalInt chosen,
        boolean decidedByTieBreak,
        boolean relic,
        boolean event) {

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
        return new PersistedDraft(floorIndex, lockIn, cards, Map.of(), OptionalInt.empty(), false, false, false);
    }

    /** A fresh RELIC draft (P34): the cards are relics, and the winner is held rather than drafted. */
    public static PersistedDraft openingRelics(int floorIndex, List<ResourceLocation> cards) {
        return new PersistedDraft(floorIndex, false, cards, Map.of(), OptionalInt.empty(), false, true, false);
    }

    /** A fresh EVENT draft (P34b): the cards are the options of an intermission room. */
    public static PersistedDraft openingEvent(int floorIndex, List<ResourceLocation> cards) {
        return new PersistedDraft(floorIndex, false, cards, Map.of(), OptionalInt.empty(), false, false, true);
    }

    public boolean resolved() {
        return chosen.isPresent();
    }

    /** The same draft with one player's vote recorded; a second vote replaces the first. */
    public PersistedDraft withVote(UUID playerId, int cardIndex) {
        if (cardIndex < 0 || cardIndex >= cards.size()) {
            throw new IllegalArgumentException("card " + cardIndex + " is not one of the " + cards.size()
                    + " offered");
        }
        Map<UUID, Integer> updated = new LinkedHashMap<>(votes);
        updated.put(playerId, cardIndex);
        return new PersistedDraft(floorIndex, lockIn, cards, updated, chosen, decidedByTieBreak, relic, event);
    }

    /** The same draft, settled. The cards stay, so what was turned down can still be read. */
    public PersistedDraft resolvedAs(int cardIndex, boolean byTieBreak) {
        return new PersistedDraft(floorIndex, lockIn, cards, votes, OptionalInt.of(cardIndex), byTieBreak, relic, event);
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
        if (relic) tag.putBoolean("relic", true);
        if (event) tag.putBoolean("event", true);
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
                tag.getBoolean("tie_break"),
                tag.getBoolean("relic"),
                tag.getBoolean("event"));
    }
}
