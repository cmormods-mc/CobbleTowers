package com.cobbletowers.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rearranging a player's collection so a run can register Pokemon from their boxes, and putting it back
 * (P18). Pure: it works on a map of {@link Slot} to Pokemon id and returns answers, never moves anything.
 *
 * <p>The whole feature is a permutation of positions -- the set of Pokemon a player owns is the same
 * before, during and after -- and a mistake here loses or duplicates someone's Pokemon, so the two
 * functions are kept small, side-effect free and heavily tested. The Cobblemon-facing adapter applies
 * their answers in two phases (take every affected Pokemon out, then set each at its target), which is
 * what stops a target ever being overwritten.
 */
public final class PartyArrangement {

    /** Cobblemon's party size. */
    public static final int PARTY_SIZE = 6;

    private PartyArrangement() {}

    public enum Failure {
        /** More chosen than the party can hold. */
        TOO_MANY,
        /** A chosen Pokemon is in neither the party nor the boxes. */
        NOT_OWNED,
        /** The boxes cannot take the party members that would be displaced. */
        NO_ROOM
    }

    /** One Pokemon and the slot it started in: what the journal keeps, and all that restore needs. */
    public record Original(UUID pokemon, Slot slot) {}

    /**
     * @param failure   why no plan could be made, or null when there is one
     * @param originals where every Pokemon that moves started, in the order they will be restored
     * @param placements where each moving Pokemon goes; empty when the party is already as chosen
     */
    public record Plan(Failure failure, List<Original> originals, Map<UUID, Slot> placements) {
        public Plan {
            originals = List.copyOf(originals);
            placements = Map.copyOf(placements);
        }

        public boolean ok() {
            return failure == null;
        }

        static Plan failed(Failure failure) {
            return new Plan(failure, List.of(), Map.of());
        }
    }

    /**
     * What to move so the party is exactly {@code chosen}, in that order.
     *
     * <p>A chosen Pokemon from a box moves up; every party member that is not chosen moves to the first
     * free box slot, because the live party is what fights and an unregistered Pokemon in it could lead.
     *
     * @param contents who is where right now, party and boxes together
     * @param pcSlots  every box slot that exists, in the order displaced Pokemon should fill them
     */
    public static Plan plan(Map<Slot, UUID> contents, List<Slot> pcSlots, List<UUID> chosen) {
        List<UUID> picks = new ArrayList<>(new LinkedHashSet<>(chosen));
        if (picks.size() > PARTY_SIZE) return Plan.failed(Failure.TOO_MANY);

        Map<UUID, Slot> where = invert(contents);
        for (UUID pick : picks) {
            if (!where.containsKey(pick)) return Plan.failed(Failure.NOT_OWNED);
        }

        // Party members in slot order. The ones not chosen are displaced.
        List<UUID> partyNow = new ArrayList<>();
        for (int slot = 0; slot < PARTY_SIZE; slot++) {
            UUID occupant = contents.get(Slot.party(slot));
            if (occupant != null) partyNow.add(occupant);
        }
        List<UUID> displaced = new ArrayList<>();
        for (UUID member : partyNow) {
            if (!picks.contains(member)) displaced.add(member);
        }

        if (alreadyArranged(contents, picks)) return new Plan(null, List.of(), Map.of());

        // A copy with everything that moves taken out, so free slots are free for real.
        Map<Slot, UUID> work = new HashMap<>(contents);
        List<UUID> moving = new ArrayList<>(picks);
        moving.addAll(displaced);
        for (UUID id : moving) work.remove(where.get(id));

        Map<UUID, Slot> placements = new LinkedHashMap<>();
        for (int i = 0; i < picks.size(); i++) placements.put(picks.get(i), Slot.party(i));

        Set<Slot> taken = new HashSet<>(work.keySet());
        for (UUID id : displaced) {
            Slot free = firstFree(pcSlots, taken);
            if (free == null) return Plan.failed(Failure.NO_ROOM);
            taken.add(free);
            placements.put(id, free);
        }

        // Only what actually changes position is journaled or moved.
        List<Original> originals = new ArrayList<>();
        Map<UUID, Slot> changes = new LinkedHashMap<>();
        for (UUID id : moving) {
            Slot from = where.get(id);
            Slot to = placements.get(id);
            if (from.equals(to)) continue;
            originals.add(new Original(id, from));
            changes.put(id, to);
        }
        return new Plan(null, originals, changes);
    }

    private static boolean alreadyArranged(Map<Slot, UUID> contents, List<UUID> picks) {
        for (int slot = 0; slot < PARTY_SIZE; slot++) {
            UUID occupant = contents.get(Slot.party(slot));
            UUID wanted = slot < picks.size() ? picks.get(slot) : null;
            if (!java.util.Objects.equals(occupant, wanted)) return false;
        }
        return true;
    }

    /**
     * @param placements  where each Pokemon that is not already at its original slot should go
     * @param missing     journaled Pokemon found nowhere: released, traded or given away. Skipped.
     * @param relocated   Pokemon placed somewhere other than their original slot because it was taken
     * @param stranded    Pokemon with nowhere at all to go (the party and every box full); left where they are
     */
    public record Restoration(Map<UUID, Slot> placements, List<UUID> missing, List<UUID> relocated,
                              List<UUID> stranded) {
        public Restoration {
            placements = Map.copyOf(placements);
            missing = List.copyOf(missing);
            relocated = List.copyOf(relocated);
            stranded = List.copyOf(stranded);
        }
    }

    /**
     * Where each journaled Pokemon should go to put the collection back.
     *
     * <p>Starts by taking every journaled Pokemon out of a copy of the layout, then places each at its
     * recorded slot if that is free. A slot that something else has taken in the meantime falls back to
     * the first free slot of the same kind, then to the boxes. Because it begins from "take them all
     * out", running it twice -- or on a layout that is already restored -- changes nothing.
     */
    public static Restoration restore(Map<Slot, UUID> contents, List<Slot> pcSlots, List<Original> originals) {
        Map<UUID, Slot> where = invert(contents);
        List<UUID> missing = new ArrayList<>();
        List<Original> present = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Original original : originals) {
            if (!seen.add(original.pokemon())) continue;   // a duplicate journal entry must not place twice
            if (where.containsKey(original.pokemon())) present.add(original);
            else missing.add(original.pokemon());
        }

        Map<Slot, UUID> work = new HashMap<>(contents);
        for (Original original : present) work.remove(where.get(original.pokemon()));
        Set<Slot> taken = new HashSet<>(work.keySet());
        Set<Slot> pcExists = new HashSet<>(pcSlots);

        // Pass one: everyone whose own slot is still free goes back to it. Done for all of them before any
        // fallback is chosen, so a fallback can never take a slot a later Pokemon needs for itself.
        Map<UUID, Slot> final_ = new LinkedHashMap<>();
        List<Original> homeless = new ArrayList<>();
        for (Original original : present) {
            Slot target = original.slot();
            boolean valid = target.isParty() ? target.index() >= 0 && target.index() < PARTY_SIZE : pcExists.contains(target);
            if (valid && !taken.contains(target)) {
                taken.add(target);
                final_.put(original.pokemon(), target);
            } else {
                homeless.add(original);
            }
        }

        // Pass two: the rest go to the first free slot of the same kind, then the boxes, then the party.
        List<UUID> relocated = new ArrayList<>();
        List<UUID> stranded = new ArrayList<>();
        for (Original original : homeless) {
            UUID id = original.pokemon();
            Slot chosen = null;
            if (original.slot().isParty()) chosen = firstFree(partySlots(), taken);
            if (chosen == null) chosen = firstFree(pcSlots, taken);
            if (chosen == null) chosen = firstFree(partySlots(), taken);
            if (chosen == null) {
                // Nowhere at all. Leave it exactly where it is, which is never a loss.
                stranded.add(id);
                taken.add(where.get(id));
                continue;
            }
            taken.add(chosen);
            final_.put(id, chosen);
            relocated.add(id);
        }

        // Only a Pokemon that is not already where it should end up needs a move.
        Map<UUID, Slot> placements = new LinkedHashMap<>();
        for (Map.Entry<UUID, Slot> entry : final_.entrySet()) {
            if (!entry.getValue().equals(where.get(entry.getKey()))) placements.put(entry.getKey(), entry.getValue());
        }
        return new Restoration(placements, missing, relocated, stranded);
    }

    private static List<Slot> partySlots() {
        List<Slot> slots = new ArrayList<>(PARTY_SIZE);
        for (int slot = 0; slot < PARTY_SIZE; slot++) slots.add(Slot.party(slot));
        return slots;
    }

    private static Slot firstFree(List<Slot> candidates, Set<Slot> taken) {
        for (Slot slot : candidates) {
            if (!taken.contains(slot)) return slot;
        }
        return null;
    }

    private static Map<UUID, Slot> invert(Map<Slot, UUID> contents) {
        Map<UUID, Slot> where = new HashMap<>();
        for (Map.Entry<Slot, UUID> entry : contents.entrySet()) where.put(entry.getValue(), entry.getKey());
        return where;
    }
}
