package com.cobbletowers.persistence;

import com.cobbletowers.storage.PartyArrangement.Original;
import com.cobbletowers.storage.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Where a player's Pokemon were before a run moved them (P18): the run and each moved Pokemon's original slot.
 * Identifiers and numbers only (TDS section 10), written to disk before any move.
 */
public record PartyJournalEntry(UUID player, UUID runId, List<Original> originals, List<UUID> rentals, List<LentCard> cards) {

    /**
     * Which set a rental came from and whether from a God Pack (P33b): enough to make the player each Pokemon's card
     * at completion even when offline.
     * @param set a rental set id, such as {@code cobbletowers:garchomp}
     */
    public record LentCard(UUID pokemon, String set, boolean god) {
        public LentCard {
            Objects.requireNonNull(pokemon, "pokemon");
            set = set == null ? "" : set;
        }
    }

    public PartyJournalEntry {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(runId, "runId");
        originals = List.copyOf(originals);
        rentals = List.copyOf(rentals);
        cards = List.copyOf(cards);
    }

    /** A journal that lent Pokemon but names no sets, which is every run before the card rewards (P33b). */
    public PartyJournalEntry(UUID player, UUID runId, List<Original> originals, List<UUID> rentals) {
        this(player, runId, originals, rentals, List.of());
    }

    /** A journal of moved Pokemon only, which is every run before the Rental Draft (P33). */
    public PartyJournalEntry(UUID player, UUID runId, List<Original> originals) {
        this(player, runId, originals, List.of(), List.of());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", player);
        tag.putUUID("run", runId);
        ListTag list = new ListTag();
        for (Original original : originals) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("pokemon", original.pokemon());
            entry.putString("kind", original.slot().kind().name().toLowerCase(Locale.ROOT));
            entry.putInt("index", original.slot().index());
            entry.putInt("sub", original.slot().sub());
            list.add(entry);
        }
        tag.put("originals", list);
        // The rentals the run was lent (P33): written with the rest, before anything moves, so a crash at any point
        // can
        // delete them again. Ids only, never a live Pokemon.
        ListTag lent = new ListTag();
        for (UUID rental : rentals) {
            CompoundTag one = new CompoundTag();
            one.putUUID("pokemon", rental);
            lent.add(one);
        }
        tag.put("rentals", lent);
        ListTag cardList = new ListTag();
        for (LentCard card : cards) {
            CompoundTag one = new CompoundTag();
            one.putUUID("pokemon", card.pokemon());
            one.putString("set", card.set());
            one.putBoolean("god", card.god());
            cardList.add(one);
        }
        tag.put("cards", cardList);
        return tag;
    }

    /** Throws on anything unreadable; the store keeps the raw tag rather than dropping a journal. */
    public static PartyJournalEntry fromTag(CompoundTag tag) {
        List<Original> originals = new ArrayList<>();
        ListTag list = tag.getList("originals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Slot.Kind kind = Slot.Kind.valueOf(entry.getString("kind").toUpperCase(Locale.ROOT));
            originals.add(new Original(entry.getUUID("pokemon"), new Slot(kind, entry.getInt("index"), entry.getInt("sub"))));
        }
        List<UUID> rentals = new ArrayList<>();
        ListTag lent = tag.getList("rentals", Tag.TAG_COMPOUND);
        for (int i = 0; i < lent.size(); i++) rentals.add(lent.getCompound(i).getUUID("pokemon"));
        List<LentCard> cards = new ArrayList<>();
        ListTag cardList = tag.getList("cards", Tag.TAG_COMPOUND);
        for (int i = 0; i < cardList.size(); i++) {
            CompoundTag one = cardList.getCompound(i);
            cards.add(new LentCard(one.getUUID("pokemon"), one.getString("set"), one.getBoolean("god")));
        }
        return new PartyJournalEntry(tag.getUUID("player"), tag.getUUID("run"), originals, rentals, cards);
    }
}
