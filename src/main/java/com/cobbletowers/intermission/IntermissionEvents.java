package com.cobbletowers.intermission;

import com.cobbletowers.api.modifier.RiskTier;
import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.encounter.EncounterSeed;
import com.cobbletowers.modifier.ModifierResolver;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.RunModifierState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Intermission events (P34b): a room that may appear between the draft and the ready-up, with a choice of two options
 * voted on exactly like a draft card.
 *
 * <p>Pure: no server, no world. A room is picked and an option resolved from the run's seed, so a shared seed shares its
 * rooms and a crash cannot re-roll a bad outcome. The one thing that is not state (a party heal) is returned as a flag
 * for the caller to apply.
 */
public final class IntermissionEvents {

    /** Its own ordinal space, like every other draw (see {@code DraftDraw}). */
    static final int EVENT_ORDINAL_BASE = 5_000_011;

    /** The chance, in percent, that an eligible intermission has a room at all. */
    public static final int ROOM_CHANCE_PERCENT = 50;

    private IntermissionEvents() {}

    public enum Room { SHRINE, GAMBLER, REST }

    public enum Option {
        SHRINE_CURSE(Room.SHRINE, "shrine_curse", "Shrine: take the curse (a relic now, a challenge for good)"),
        SHRINE_LEAVE(Room.SHRINE, "shrine_leave", "Shrine: walk away"),
        GAMBLER_STAKE(Room.GAMBLER, "gambler_stake", "Gambler: stake a relic (even odds: win another, or lose it)"),
        GAMBLER_PASS(Room.GAMBLER, "gambler_pass", "Gambler: keep your relics"),
        REST_HEAL(Room.REST, "rest_heal", "Rest: fully heal the party"),
        REST_PRESS_ON(Room.REST, "rest_press_on", "Press on: no rest");

        private final Room room;
        private final String id;
        private final String label;

        Option(Room room, String id, String label) {
            this.room = room;
            this.id = id;
            this.label = label;
        }

        public Room room() {
            return room;
        }

        public String label() {
            return label;
        }

        /** The draft-card id of this option. */
        public ResourceLocation cardId() {
            return ResourceLocation.fromNamespaceAndPath("cobbletowers", "event/" + id);
        }

        public static Optional<Option> fromCard(ResourceLocation card) {
            return Arrays.stream(values()).filter(option -> option.cardId().equals(card)).findFirst();
        }
    }

    /** What an option did: the new state, whether the party is healed, and a sentence for the team. */
    public record Outcome(RunModifierState state, boolean healParty, String message) {}

    /** An operator can switch rooms off with {@code -Dcobbletowers.events=off} (also used by tests of the draft alone). */
    public static boolean enabled() {
        return !"off".equalsIgnoreCase(System.getProperty("cobbletowers.events", "on"));
    }

    public static long seedOf(PersistedRun run, int floorIndex) {
        return EncounterSeed.of(run.seed(), floorIndex, EVENT_ORDINAL_BASE);
    }

    // ---- choosing a room -------------------------------------------------------------------------

    /** The room this intermission has, if any: not on a milestone floor (those pay a relic), and half the time. */
    public static Optional<Room> roomFor(TowerContent content, PersistedRun run, int floorIndex) {
        if (!enabled()) return Optional.empty();
        if (content.milestoneAt(run.towerId(), floorIndex).isPresent()) return Optional.empty();
        if (Math.floorMod(seedOf(run, floorIndex), 100) >= ROOM_CHANCE_PERCENT) return Optional.empty();
        List<Room> eligible = new ArrayList<>();
        for (Room room : Room.values()) {
            if (eligible(content, run, floorIndex, room)) eligible.add(room);
        }
        if (eligible.isEmpty()) return Optional.empty();
        long pick = EncounterSeed.of(run.seed(), floorIndex, EVENT_ORDINAL_BASE + 1);
        return Optional.of(eligible.get((int) Math.floorMod(pick, eligible.size())));
    }

    static boolean eligible(TowerContent content, PersistedRun run, int floorIndex, Room room) {
        RunModifierState state = run.modifiers();
        return switch (room) {
            case SHRINE -> state.hasRelicRoom() && !relicChoices(content, state).isEmpty()
                    && !curseChoices(content, run, floorIndex).isEmpty();
            case GAMBLER -> !state.relics().isEmpty();
            case REST -> true;
        };
    }

    /** The two cards of a room, in a fixed order. */
    public static List<ResourceLocation> cardsOf(Room room) {
        return Arrays.stream(Option.values()).filter(option -> option.room() == room).map(Option::cardId).toList();
    }

    // ---- resolving an option ---------------------------------------------------------------------

    public static Outcome resolve(TowerContent content, PersistedRun run, int floorIndex, Option option) {
        RunModifierState state = run.modifiers();
        long seed = seedOf(run, floorIndex);
        switch (option) {
            case SHRINE_CURSE -> {
                List<ModifierDefinition> curses = curseChoices(content, run, floorIndex);
                List<ModifierDefinition> relics = relicChoices(content, state);
                if (curses.isEmpty() || relics.isEmpty() || !state.hasRelicRoom()) {
                    return new Outcome(state, false, "The shrine is silent.");
                }
                ModifierDefinition curse = curses.get((int) Math.floorMod(seed >> 3, curses.size()));
                ModifierDefinition relic = relics.get((int) Math.floorMod(seed >> 7, relics.size()));
                return new Outcome(state.accumulating(curse.id()).withRelic(relic.id()), false,
                        "The shrine takes its price (" + curse.displayName() + ") and gives " + relic.displayName() + ".");
            }
            case GAMBLER_STAKE -> {
                if (state.relics().isEmpty()) return new Outcome(state, false, "You have nothing to stake.");
                ResourceLocation staked = state.relics().get((int) Math.floorMod(seed >> 3, state.relics().size()));
                if (Math.floorMod(seed >> 11, 2) == 0) {
                    List<ModifierDefinition> relics = relicChoices(content, state);
                    if (relics.isEmpty() || !state.hasRelicRoom()) {
                        return new Outcome(state, false, "You win, but there is nothing left to win.");
                    }
                    ModifierDefinition won = relics.get((int) Math.floorMod(seed >> 7, relics.size()));
                    return new Outcome(state.withRelic(won.id()), false, "The gambler pays out: " + won.displayName() + ".");
                }
                return new Outcome(state.withoutRelic(staked), false, "The gambler takes your stake.");
            }
            case REST_HEAL -> {
                return new Outcome(state, true, "The party rests and is fully healed.");
            }
            default -> {
                return new Outcome(state, false, "You press on.");
            }
        }
    }

    /** Relics the run could take now, in the pool's stable order. */
    private static List<ModifierDefinition> relicChoices(TowerContent content, RunModifierState state) {
        List<ModifierDefinition> held = new ArrayList<>();
        for (ResourceLocation id : state.relics()) content.modifier(id).ifPresent(held::add);
        return ModifierResolver.eligibleFrom(content.relicPool(), held);
    }

    /** The challenges a shrine may charge: legal to hold, and not a trivial one if anything harsher exists. */
    private static List<ModifierDefinition> curseChoices(TowerContent content, PersistedRun run, int floorIndex) {
        List<ModifierDefinition> held = new ArrayList<>();
        for (ResourceLocation id : run.modifiers().accumulated()) content.modifier(id).ifPresent(held::add);
        List<ModifierDefinition> eligible = ModifierResolver.eligibleFrom(
                content.draftablePool(run.towerId(), floorIndex), held);
        List<ModifierDefinition> harsh = eligible.stream().filter(modifier -> modifier.risk() != RiskTier.MINOR).toList();
        return harsh.isEmpty() ? eligible : harsh;
    }
}
