package com.cobbletowers.intermission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.TestRuns;
import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.intermission.IntermissionEvents.Option;
import com.cobbletowers.intermission.IntermissionEvents.Room;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.RunModifierState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Intermission event rooms (P34b): when they appear, and what each option does. */
class IntermissionEventsTest {

    private static final UUID RUN = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private static ModifierDefinition relic(String path) {
        return TestRuns.modifier(path, "reward", "\"reward_percent\":125", "\"relic\":true", "\"tags\":[\"economy\"]");
    }

    private static ModifierDefinition curse(String path) {
        return TestRuns.modifier(path, "enemy", "\"level_offset\":3", "\"risk\":\"severe\"");
    }

    private static TowerContent content(ModifierDefinition... modifiers) {
        Map<ResourceLocation, ModifierDefinition> all = new HashMap<>();
        for (ModifierDefinition modifier : modifiers) all.put(modifier.id(), modifier);
        return TestRuns.contentWith(all);
    }

    private static PersistedRun run(long seed, int floor, RunModifierState state) {
        PersistedRun base = TestRuns.fresh(RUN);
        return new PersistedRun(base.runId(), base.schemaVersion(), base.towerId(), base.towerRevision(),
                base.towerDigest(), base.rulesetRevision(), base.structureRevision(), seed, floor, base.state(),
                base.participants(), base.lastCheckpoint(), base.committedTransactions(), base.updatedAt(),
                base.cell(), base.ledger(), state, base.lastBankedFloor(), base.vendorPurchases());
    }

    @Test
    @DisplayName("every room offers two options, each a card the option table can read back")
    void roomsHaveTwoReadableOptions() {
        for (Room room : Room.values()) {
            assertEquals(2, IntermissionEvents.cardsOf(room).size(), room + " offers two options");
            for (ResourceLocation card : IntermissionEvents.cardsOf(room)) {
                assertEquals(room, Option.fromCard(card).orElseThrow().room());
            }
        }
    }

    @Test
    @DisplayName("a milestone floor never has a room; it pays a relic instead")
    void noRoomOnMilestones() {
        TowerContent content = content();
        for (long seed = 0; seed < 200; seed++) {
            assertTrue(IntermissionEvents.roomFor(content, run(seed, 2, RunModifierState.EMPTY), 2).isEmpty());
        }
    }

    @Test
    @DisplayName("an ordinary floor has a room about half the time, and the same seed always gives the same answer")
    void roomChance() {
        TowerContent content = content();
        int rooms = 0;
        for (long seed = 0; seed < 400; seed++) {
            PersistedRun run = run(seed, 1, RunModifierState.EMPTY);
            var first = IntermissionEvents.roomFor(content, run, 1);
            assertEquals(first, IntermissionEvents.roomFor(content, run, 1));
            if (first.isPresent()) rooms++;
        }
        assertTrue(rooms > 140 && rooms < 260, "expected about 200 of 400, got " + rooms);
    }

    @Test
    @DisplayName("the shrine needs a relic to give and a challenge to charge; the gambler needs a relic to stake")
    void eligibility() {
        TowerContent bare = content();
        PersistedRun empty = run(1, 1, RunModifierState.EMPTY);
        assertTrue(IntermissionEvents.eligible(bare, empty, 1, Room.REST));
        assertFalse(IntermissionEvents.eligible(bare, empty, 1, Room.SHRINE));
        assertFalse(IntermissionEvents.eligible(bare, empty, 1, Room.GAMBLER));

        ModifierDefinition lucky = relic("lucky");
        TowerContent full = content(lucky, curse("harder"));
        assertTrue(IntermissionEvents.eligible(full, empty, 1, Room.SHRINE));
        assertFalse(IntermissionEvents.eligible(full, empty, 1, Room.GAMBLER));
        assertTrue(IntermissionEvents.eligible(full, run(1, 1, RunModifierState.EMPTY.withRelic(lucky.id())), 1,
                Room.GAMBLER));
    }

    @Test
    @DisplayName("taking the curse adds a challenge and a relic; walking away changes nothing")
    void shrine() {
        ModifierDefinition lucky = relic("lucky");
        ModifierDefinition harder = curse("harder");
        TowerContent content = content(lucky, harder);
        PersistedRun run = run(7, 1, RunModifierState.EMPTY);

        var taken = IntermissionEvents.resolve(content, run, 1, Option.SHRINE_CURSE);
        assertEquals(java.util.List.of(harder.id()), taken.state().accumulated());
        assertEquals(java.util.List.of(lucky.id()), taken.state().relics());

        var left = IntermissionEvents.resolve(content, run, 1, Option.SHRINE_LEAVE);
        assertEquals(run.modifiers(), left.state());
    }

    @Test
    @DisplayName("a gamble either wins a second relic or loses the staked one, by seed, and passing changes nothing")
    void gambler() {
        ModifierDefinition a = relic("a");
        ModifierDefinition b = relic("b");
        TowerContent content = content(a, b);
        boolean won = false;
        boolean lost = false;
        for (long seed = 0; seed < 60 && !(won && lost); seed++) {
            PersistedRun run = run(seed, 1, RunModifierState.EMPTY.withRelic(a.id()));
            var outcome = IntermissionEvents.resolve(content, run, 1, Option.GAMBLER_STAKE);
            if (outcome.state().relics().size() == 2) {
                won = true;
                assertTrue(outcome.state().relics().contains(b.id()));
            } else {
                lost = true;
                assertTrue(outcome.state().relics().isEmpty());
            }
            assertEquals(outcome, IntermissionEvents.resolve(content, run, 1, Option.GAMBLER_STAKE),
                    "a crash and a replay cannot turn a lost gamble into a won one");
            assertEquals(run.modifiers(), IntermissionEvents.resolve(content, run, 1, Option.GAMBLER_PASS).state());
        }
        assertTrue(won && lost, "both outcomes are reachable");
    }

    @Test
    @DisplayName("resting heals the party; pressing on does not")
    void rest() {
        PersistedRun run = run(1, 1, RunModifierState.EMPTY);
        assertTrue(IntermissionEvents.resolve(content(), run, 1, Option.REST_HEAL).healParty());
        assertFalse(IntermissionEvents.resolve(content(), run, 1, Option.REST_PRESS_ON).healParty());
    }
}
