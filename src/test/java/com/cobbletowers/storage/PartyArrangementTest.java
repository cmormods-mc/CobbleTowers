package com.cobbletowers.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.storage.PartyArrangement.Failure;
import com.cobbletowers.storage.PartyArrangement.Original;
import com.cobbletowers.storage.PartyArrangement.Plan;
import com.cobbletowers.storage.PartyArrangement.Restoration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P18's safety argument: a permutation of positions never loses or duplicates a Pokemon and restoring puts the
 * collection back.
 */
class PartyArrangementTest {

    private static final int BOXES = 2;
    private static final int BOX_SLOTS = 30;
    private static final List<Slot> PC = pcSlots(BOXES);

    private static List<Slot> pcSlots(int boxes) {
        List<Slot> slots = new ArrayList<>();
        for (int box = 0; box < boxes; box++) {
            for (int slot = 0; slot < BOX_SLOTS; slot++) slots.add(Slot.pc(box, slot));
        }
        return slots;
    }

    private static UUID id(int n) {
        return new UUID(0, n);
    }

    /** Party slots 0..partyCount-1 hold ids 1..partyCount; the boxes' first slots hold {@code boxed}. */
    private static Map<Slot, UUID> layout(int partyCount, int... boxed) {
        Map<Slot, UUID> contents = new HashMap<>();
        for (int i = 0; i < partyCount; i++) contents.put(Slot.party(i), id(i + 1));
        for (int i = 0; i < boxed.length; i++) contents.put(PC.get(i), id(boxed[i]));
        return contents;
    }

    /** Applies placements to a copy of the layout the way the adapter's two phases do: out, then in. */
    private static Map<Slot, UUID> apply(Map<Slot, UUID> contents, Map<UUID, Slot> placements) {
        Map<Slot, UUID> next = new HashMap<>(contents);
        next.values().removeAll(placements.keySet());
        placements.forEach((pokemon, slot) -> {
            assertFalse(next.containsKey(slot), "a target was already occupied: " + slot);
            next.put(slot, pokemon);
        });
        return next;
    }

    private static TreeSet<UUID> everyone(Map<Slot, UUID> contents) {
        return new TreeSet<>(contents.values());
    }

    @Test
    @DisplayName("a boxed Pokemon is promoted into the party and a displaced one goes to a box")
    void promotesFromBox() {
        Map<Slot, UUID> before = layout(6, 7, 8);
        Plan plan = PartyArrangement.plan(before, PC, List.of(id(7), id(8)));

        assertTrue(plan.ok());
        Map<Slot, UUID> after = apply(before, plan.placements());
        assertEquals(id(7), after.get(Slot.party(0)));
        assertEquals(id(8), after.get(Slot.party(1)));
        for (int slot = 2; slot < 6; slot++) {
            assertFalse(after.containsKey(Slot.party(slot)), "only the chosen stay in the party");
        }
        assertEquals(everyone(before), everyone(after), "nobody was lost or duplicated");
        assertEquals(before.size(), after.size());
    }

    @Test
    @DisplayName("reordering inside the party moves only what has to move")
    void reordersInParty() {
        Map<Slot, UUID> before = layout(3);
        Plan plan = PartyArrangement.plan(before, PC, List.of(id(3), id(1), id(2)));

        assertTrue(plan.ok());
        Map<Slot, UUID> after = apply(before, plan.placements());
        assertEquals(id(3), after.get(Slot.party(0)));
        assertEquals(id(1), after.get(Slot.party(1)));
        assertEquals(id(2), after.get(Slot.party(2)));
    }

    @Test
    @DisplayName("a party already exactly as chosen plans nothing")
    void alreadyArranged() {
        Plan plan = PartyArrangement.plan(layout(3), PC, List.of(id(1), id(2), id(3)));

        assertTrue(plan.ok());
        assertEquals(List.of(), plan.originals());
        assertEquals(Map.of(), plan.placements());
    }

    @Test
    @DisplayName("a Pokemon that does not move is not journaled")
    void unmovedNotJournaled() {
        Plan plan = PartyArrangement.plan(layout(3, 9), PC, List.of(id(1), id(9)));

        List<UUID> journaled = plan.originals().stream().map(Original::pokemon).toList();
        assertFalse(journaled.contains(id(1)), "Pokemon 1 stays in slot 0");
        assertTrue(journaled.contains(id(9)));
        assertTrue(journaled.contains(id(2)) && journaled.contains(id(3)), "the displaced are journaled");
    }

    @Test
    @DisplayName("choosing something owned by nobody here fails before anything moves")
    void notOwned() {
        Plan plan = PartyArrangement.plan(layout(3), PC, List.of(id(1), id(99)));

        assertEquals(Failure.NOT_OWNED, plan.failure());
        assertEquals(Map.of(), plan.placements());
    }

    @Test
    @DisplayName("more than six chosen fails")
    void tooMany() {
        Plan plan = PartyArrangement.plan(layout(6, 7), PC,
                List.of(id(1), id(2), id(3), id(4), id(5), id(6), id(7)));

        assertEquals(Failure.TOO_MANY, plan.failure());
    }

    @Test
    @DisplayName("full boxes fail with NO_ROOM, never by dropping a Pokemon")
    void noRoom() {
        Map<Slot, UUID> before = new HashMap<>();
        for (int i = 0; i < 6; i++) before.put(Slot.party(i), id(i + 1));
        List<Slot> tiny = List.of(Slot.pc(0, 0));
        before.put(tiny.get(0), id(50));

        // Choosing the boxed one frees its slot for one displaced Pokemon, but six are displaced.
        Plan plan = PartyArrangement.plan(before, tiny, List.of(id(50)));

        assertEquals(Failure.NO_ROOM, plan.failure());
    }

    @Test
    @DisplayName("duplicates in the chosen list are ignored")
    void duplicatesInChosen() {
        Plan plan = PartyArrangement.plan(layout(3), PC, List.of(id(2), id(2), id(1)));

        assertTrue(plan.ok());
        Map<Slot, UUID> after = apply(layout(3), plan.placements());
        assertEquals(id(2), after.get(Slot.party(0)));
        assertEquals(id(1), after.get(Slot.party(1)));
    }

    @Test
    @DisplayName("restoring after a plan puts every Pokemon exactly back")
    void restoreIsTheInverse() {
        Map<Slot, UUID> before = layout(6, 7, 8, 9);
        Plan plan = PartyArrangement.plan(before, PC, List.of(id(8), id(2), id(9)));
        Map<Slot, UUID> during = apply(before, plan.placements());

        Restoration restoration = PartyArrangement.restore(during, PC, plan.originals());

        assertEquals(before, apply(during, restoration.placements()));
        assertEquals(List.of(), restoration.missing());
        assertEquals(List.of(), restoration.relocated());
    }

    @Test
    @DisplayName("restoring twice, or on a layout the plan never touched, changes nothing")
    void restoreIsIdempotent() {
        Map<Slot, UUID> before = layout(6, 7, 8);
        Plan plan = PartyArrangement.plan(before, PC, List.of(id(7)));
        Map<Slot, UUID> during = apply(before, plan.placements());
        Map<Slot, UUID> restored = apply(during, PartyArrangement.restore(during, PC, plan.originals()).placements());

        assertEquals(before, restored);
        assertEquals(Map.of(), PartyArrangement.restore(restored, PC, plan.originals()).placements());
        // A crash before the moves left the layout as it was: restore must be a no-op there too.
        assertEquals(Map.of(), PartyArrangement.restore(before, PC, plan.originals()).placements());
    }

    @Test
    @DisplayName("a Pokemon released during the run is reported missing and the rest are restored")
    void missingPokemon() {
        Map<Slot, UUID> before = layout(6, 7);
        Plan plan = PartyArrangement.plan(before, PC, List.of(id(7)));
        Map<Slot, UUID> during = apply(before, plan.placements());
        during.values().remove(id(3));

        Restoration restoration = PartyArrangement.restore(during, PC, plan.originals());

        assertEquals(List.of(id(3)), restoration.missing());
        Map<Slot, UUID> after = apply(during, restoration.placements());
        for (int slot : new int[] {0, 1, 3, 4, 5}) assertEquals(id(slot + 1), after.get(Slot.party(slot)));
        assertEquals(id(7), after.get(PC.get(0)));
    }

    @Test
    @DisplayName("an original slot something else took is replaced by the next free one, never overwritten")
    void takenSlotFallsBack() {
        Map<Slot, UUID> before = layout(2, 7);
        Plan plan = PartyArrangement.plan(before, PC, List.of(id(7)));
        Map<Slot, UUID> during = apply(before, plan.placements());
        // The player boxed Pokemon 7 again and a newcomer took party slot 0, where Pokemon 1 started.
        during.remove(Slot.party(0));
        during.put(PC.get(5), id(7));
        UUID newcomer = id(40);
        during.put(Slot.party(0), newcomer);

        Restoration restoration = PartyArrangement.restore(during, PC, plan.originals());
        Map<Slot, UUID> after = apply(during, restoration.placements());

        assertEquals(newcomer, after.get(Slot.party(0)), "the newcomer was not overwritten");
        assertEquals(id(2), after.get(Slot.party(1)), "2 went back to its own slot");
        assertEquals(id(1), after.get(Slot.party(2)), "1 took the next free party slot");
        assertEquals(id(7), after.get(PC.get(0)), "7 went back to the box slot it came from");
        assertEquals(List.of(id(1)), restoration.relocated());
    }

    @Test
    @DisplayName("a fallback never takes a slot a later Pokemon needs for itself")
    void fallbackLeavesLaterHomesAlone() {
        Map<Slot, UUID> before = layout(0, 7, 8);
        Plan plan = PartyArrangement.plan(before, PC, List.of(id(7), id(8)));
        Map<Slot, UUID> during = apply(before, plan.placements());
        // Both came out of the box; a stranger now sits in 7's old slot.
        during.put(PC.get(0), id(40));

        Restoration restoration = PartyArrangement.restore(during, PC, plan.originals());
        Map<Slot, UUID> after = apply(during, restoration.placements());

        assertEquals(id(8), after.get(PC.get(1)), "8 got its own slot, not 7's fallback");
        assertEquals(id(40), after.get(PC.get(0)), "the stranger was not overwritten");
        assertTrue(after.containsValue(id(7)));
        assertEquals(List.of(id(7)), restoration.relocated());
    }

    @Test
    @DisplayName("a duplicate journal entry does not place a Pokemon twice")
    void duplicateJournalEntry() {
        Map<Slot, UUID> before = layout(6, 7);
        Plan plan = PartyArrangement.plan(before, PC, List.of(id(7)));
        Map<Slot, UUID> during = apply(before, plan.placements());
        List<Original> doubled = new ArrayList<>(plan.originals());
        doubled.addAll(plan.originals());

        Restoration restoration = PartyArrangement.restore(during, PC, doubled);

        assertEquals(before, apply(during, restoration.placements()));
    }

    @Test
    @DisplayName("random collections: nobody is ever lost or duplicated, and restore always inverts plan")
    void randomisedPermutations() {
        Random random = new Random(20261002L);
        for (int round = 0; round < 3000; round++) {
            int partyCount = random.nextInt(7);
            int boxedCount = random.nextInt(25);
            List<UUID> all = new ArrayList<>();
            for (int i = 0; i < partyCount + boxedCount; i++) all.add(id(i + 1));

            Map<Slot, UUID> before = new HashMap<>();
            List<Slot> partySlots = new ArrayList<>();
            for (int i = 0; i < 6; i++) partySlots.add(Slot.party(i));
            Collections.shuffle(partySlots, random);
            for (int i = 0; i < partyCount; i++) before.put(partySlots.get(i), all.get(i));
            List<Slot> boxSlots = new ArrayList<>(PC);
            Collections.shuffle(boxSlots, random);
            for (int i = 0; i < boxedCount; i++) before.put(boxSlots.get(i), all.get(partyCount + i));

            List<UUID> shuffled = new ArrayList<>(all);
            Collections.shuffle(shuffled, random);
            List<UUID> chosen = shuffled.subList(0, Math.min(shuffled.size(), random.nextInt(7)));

            Plan plan = PartyArrangement.plan(before, PC, chosen);
            assertTrue(plan.ok(), "round " + round);
            Map<Slot, UUID> during = apply(before, plan.placements());

            assertEquals(everyone(before), everyone(during), "round " + round + ": the set of Pokemon changed");
            assertEquals(before.size(), during.size(), "round " + round + ": a Pokemon was duplicated or dropped");
            for (int slot = 0; slot < 6; slot++) {
                UUID wanted = slot < chosen.size() ? chosen.get(slot) : null;
                assertEquals(wanted, during.get(Slot.party(slot)), "round " + round + ": party slot " + slot);
            }

            Restoration restoration = PartyArrangement.restore(during, PC, plan.originals());
            assertEquals(before, apply(during, restoration.placements()), "round " + round + ": not restored exactly");
            assertEquals(List.of(), restoration.relocated());
            assertEquals(List.of(), restoration.stranded());
        }
    }
}
