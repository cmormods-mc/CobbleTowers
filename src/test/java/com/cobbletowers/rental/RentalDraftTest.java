package com.cobbletowers.rental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.definition.RentalSetDefinition.Rarity;
import com.cobbletowers.rental.RentalDraft.Result;
import com.cobbletowers.rental.RentalDraw.Offer;
import com.cobbletowers.rental.RentalDraw.Pack;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The rental draft's pure half (P33): the sets, the draw, and the draft rules. */
class RentalDraftTest {

    private static final Path DIR = Paths.get("src/main/resources/data/cobbletowers/cobbletowers/rental_sets");

    private static List<RentalSetDefinition> pool() throws IOException {
        List<RentalSetDefinition> all = new ArrayList<>();
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", file.getFileName().toString().replace(".json", ""));
                try (Reader reader = Files.newBufferedReader(file)) {
                    all.add(RentalSetDefinition.fromJson(id, JsonParser.parseReader(reader).getAsJsonObject()));
                }
            }
        }
        return all;
    }

    private static long count(Pack pack, Rarity rarity) {
        return pack.cards().stream().filter(card -> card.rarity() == rarity).count();
    }

    // ---- the sets
    // --------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the shipped sets parse, each species appears once, and there are enough of each rarity to draw from")
    void sets() throws IOException {
        List<RentalSetDefinition> all = pool();
        assertTrue(all.size() >= 100, "a wide pool: " + all.size());
        assertEquals(all.size(), all.stream().map(RentalSetDefinition::species).distinct().count(), "one set per species");
        // Types stay evenly spread: every one of the eighteen leads at least six sets.
        for (String type : RentalSetDefinition.TYPE_NAMES) {
            long n = all.stream().filter(set -> set.types().get(0).equals(type)).count();
            assertTrue(n >= 6, type + " leads only " + n + " set(s)");
        }
        for (Rarity rarity : List.of(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY)) {
            long n = all.stream().filter(set -> set.rarity() == rarity).count();
            assertTrue(n >= 4, rarity + " has " + n);
        }
        for (RentalSetDefinition set : all) {
            assertEquals(4, set.moves().size(), set.species());
            assertEquals(50, set.level(), set.species());
            assertTrue(set.evs().stream().mapToInt(Integer::intValue).sum() <= 510, set.species());
            assertTrue(set.item().isPresent() && set.item().get().getNamespace().equals("cobblemon"), set.species());
        }
    }

    @Test
    @DisplayName("a set turns into the property string Cobblemon parses")
    void properties() throws IOException {
        RentalSetDefinition garchomp = pool().stream().filter(set -> set.species().equals("garchomp")).findFirst().orElseThrow();
        assertEquals("garchomp level=50 nature=jolly ability=roughskin moves=earthquake,outrage,stoneedge,firefang held_item=cobblemon:life_orb",
                garchomp.properties());
        assertEquals("Garchomp", garchomp.displayName());
    }

    @Test
    @DisplayName("a bad set is refused: EVs over the limit, a fifth move, an unknown rarity, a bad level")
    void refusesBad() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", "x");
        String base = "\"schema_version\":1,\"species\":\"pikachu\",\"ability\":\"static\",\"moves\":[\"thunderbolt\"],\"rarity\":\"common\"";
        assertTrue(RentalSetDefinition.fromJson(id, JsonParser.parseString("{" + base + "}").getAsJsonObject()).level() == 50);
        assertThrows(IllegalArgumentException.class, () -> RentalSetDefinition.fromJson(id, JsonParser.parseString(
                "{" + base + ",\"evs\":[252,252,252,0,0,0]}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> RentalSetDefinition.fromJson(id, JsonParser.parseString(
                "{" + base.replace("[\"thunderbolt\"]", "[\"a\",\"b\",\"c\",\"d\",\"e\"]") + "}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> RentalSetDefinition.fromJson(id, JsonParser.parseString(
                "{" + base.replace("common", "ultra") + "}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> RentalSetDefinition.fromJson(id, JsonParser.parseString(
                "{" + base + ",\"level\":101}").getAsJsonObject()));
    }

    // ---- the draw
    // --------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a normal pack is three common, one uncommon and one rare or better; a draft is three packs of five")
    void shape() throws IOException {
        List<RentalSetDefinition> pool = pool();
        for (long seed = 0; seed < 300; seed++) {
            Offer offer = RentalDraw.draw(pool, seed, false);
            assertEquals(3, offer.packs().size());
            for (Pack pack : offer.packs()) {
                assertEquals(5, pack.cards().size());
                assertFalse(pack.god());
                assertEquals(3, count(pack, Rarity.COMMON), "seed " + seed + " " + pack);
                // the upgrade guarantee may replace the top slot, never a common or the uncommon
                long upper = pack.cards().stream().filter(card -> card.rarity().atLeast(Rarity.RARE)).count();
                assertEquals(1, upper, "seed " + seed);
                assertEquals(1, count(pack, Rarity.UNCOMMON), "seed " + seed);
            }
        }
    }

    @Test
    @DisplayName("at least one epic-or-better card is offered in every draft, over thousands of seeds")
    void guarantee() throws IOException {
        List<RentalSetDefinition> pool = pool();
        for (long seed = 0; seed < 5000; seed++) {
            assertTrue(RentalDraw.draw(pool, seed, false).hasEpicOrBetter(), "seed " + seed);
            assertTrue(RentalDraw.draw(pool, seed, true).hasEpicOrBetter(), "seed " + seed + " with god packs");
        }
    }

    @Test
    @DisplayName("no species is offered twice in one draft")
    void noRepeats() throws IOException {
        List<RentalSetDefinition> pool = pool();
        for (long seed = 0; seed < 1000; seed++) {
            Set<String> species = new HashSet<>();
            for (Pack pack : RentalDraw.draw(pool, seed, true).packs()) {
                for (RentalSetDefinition card : pack.cards()) assertTrue(species.add(card.species()), "seed " + seed + " " + card.species());
            }
            assertEquals(15, species.size());
        }
    }

    @Test
    @DisplayName("the draw is a pure function of the seed: same seed, same packs in the same order; another seed differs")
    void deterministic() throws IOException {
        List<RentalSetDefinition> pool = pool();
        assertEquals(RentalDraw.draw(pool, 42, true), RentalDraw.draw(pool, 42, true));
        assertNotEquals(RentalDraw.draw(pool, 42, true), RentalDraw.draw(pool, 43, true));
    }

    @Test
    @DisplayName("a God Pack turns up about three times in a hundred, is five epic-or-better cards, and never when disallowed")
    void godPack() throws IOException {
        List<RentalSetDefinition> pool = pool();
        int gods = 0;
        for (long seed = 0; seed < 20_000; seed++) {
            Offer offer = RentalDraw.draw(pool, seed, true);
            if (offer.hasGodPack()) {
                gods++;
                Pack god = offer.packs().stream().filter(Pack::god).findFirst().orElseThrow();
                assertTrue(god.cards().stream().allMatch(card -> card.rarity().atLeast(Rarity.RARE)), "seed " + seed);
                assertTrue(god.cards().stream().filter(card -> card.rarity().atLeast(Rarity.EPIC)).count() >= 3, "mostly epic or better");
                assertTrue(god.cards().stream().filter(card -> card.rarity().isTop()).count() <= 2, "so a team can keep two that are not");
            }
            assertFalse(RentalDraw.draw(pool, seed, false).hasGodPack(), "trials never roll one");
        }
        assertTrue(gods > 20_000 * 0.02 && gods < 20_000 * 0.04, "god packs: " + gods);
    }

    @Test
    @DisplayName("a pool too small to avoid repeats still gives a draft, and an empty pool says so")
    void smallPool() throws IOException {
        List<RentalSetDefinition> few = pool().subList(0, 6);
        assertEquals(3, RentalDraw.draw(few, 1, false).packs().size());
        assertThrows(IllegalArgumentException.class, () -> RentalDraw.draw(List.of(), 1, false));
    }

    // ---- the draft
    // -------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a draft keeps two cards from each pack in order and finishes with six Pokemon, each with its own id")
    void flow() throws IOException {
        RentalDraft draft = new RentalDraft(RentalDraw.draw(pool(), 7, false));
        assertEquals(0, draft.currentPack());
        assertFalse(draft.complete());
        assertNull(draft.finish(UUID::randomUUID), "not finished yet");
        assertEquals(Result.OK, draft.pick(0, List.of(0, 1)));
        assertEquals(Result.OK, draft.pick(1, List.of(2, 3)));
        assertEquals(Result.OK, draft.pick(2, List.of(4, 0)));
        assertTrue(draft.complete());
        RentalDraft.Team team = draft.finish(UUID::randomUUID);
        assertEquals(6, team.sets().size());
        assertEquals(6, new HashSet<>(team.ids()).size());
        assertEquals(draft.team(), team.sets());
        assertEquals(Result.ALREADY_COMPLETE, draft.pick(2, List.of(0, 1)));
    }

    @Test
    @DisplayName("every bad pick is refused with a reason and changes nothing")
    void badPicks() throws IOException {
        RentalDraft draft = new RentalDraft(RentalDraw.draw(pool(), 7, false));
        assertEquals(Result.NOT_THIS_PACK, draft.pick(1, List.of(0, 1)));
        assertEquals(Result.WRONG_COUNT, draft.pick(0, List.of(0)));
        assertEquals(Result.WRONG_COUNT, draft.pick(0, List.of(0, 1, 2)));
        assertEquals(Result.DUPLICATE, draft.pick(0, List.of(2, 2)));
        assertEquals(Result.OUT_OF_RANGE, draft.pick(0, List.of(0, 5)));
        assertEquals(Result.OUT_OF_RANGE, draft.pick(0, List.of(-1, 0)));
        assertEquals(0, draft.currentPack());
        assertTrue(draft.team().isEmpty());
    }

    @Test
    @DisplayName("a team may keep at most two legendary or mythic tier cards, however the packs fall")
    void legendaryCap() throws IOException {
        List<RentalSetDefinition> pool = pool();
        int refused = 0;
        for (long seed = 0; seed < 4000 && refused < 10; seed++) {
            RentalDraft draft = new RentalDraft(RentalDraw.draw(pool, seed, true));
            // greedily try to keep the rarest two of every pack
            for (int pack = 0; pack < 3; pack++) {
                List<Rarity> cards = draft.offer().packs().get(pack).tiers();
                List<Integer> order = new ArrayList<>();
                for (int i = 0; i < cards.size(); i++) order.add(i);
                order.sort((a, b) -> cards.get(b).compareTo(cards.get(a)));
                Result result = draft.pick(pack, List.of(order.get(0), order.get(1)));
                if (result == Result.TOO_MANY_LEGENDARY) {
                    refused++;
                    // the two least rare of that pack are always keepable
                    assertEquals(Result.OK, draft.pick(pack, List.of(order.get(3), order.get(4))));
                }
            }
            assertTrue(draft.teamTiers().stream().filter(Rarity::isTop).count() <= RentalDraft.MAX_TOP_RARITY, "seed " + seed);
        }
    }

    // ---- the tiers (P53)
    // -------------------------------------------------------------------------------------------------

    private static RentalSetDefinition ofClass(List<RentalSetDefinition> pool, Rarity rarity, int skip) {
        return pool.stream().filter(set -> set.rarity() == rarity).skip(skip).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a normal pack deals three common, one uncommon and one rare-or-better tier, whatever the species are")
    void tierShape() throws IOException {
        List<RentalSetDefinition> pool = pool();
        for (long seed = 0; seed < 500; seed++) {
            for (Pack pack : RentalDraw.draw(pool, seed, false).packs()) {
                assertEquals(pack.cards().size(), pack.tiers().size());
                assertEquals(3, pack.tiers().stream().filter(tier -> tier == Rarity.COMMON).count(), "seed " + seed);
                assertEquals(1, pack.tiers().stream().filter(tier -> tier == Rarity.UNCOMMON).count(), "seed " + seed);
                assertEquals(1, pack.tiers().stream().filter(tier -> tier.atLeast(Rarity.RARE)).count(), "seed " + seed);
            }
        }
    }

    @Test
    @DisplayName("at least one epic-or-better tier is dealt in every draft, and a God Pack is epic-or-better tiers with at most two top")
    void tierGuarantees() throws IOException {
        List<RentalSetDefinition> pool = pool();
        for (long seed = 0; seed < 5000; seed++) {
            Offer offer = RentalDraw.draw(pool, seed, true);
            assertTrue(offer.hasEpicTierOrBetter(), "seed " + seed);
            for (Pack pack : offer.packs()) {
                if (!pack.god()) continue;
                assertTrue(pack.tiers().stream().allMatch(tier -> tier.atLeast(Rarity.EPIC)), "seed " + seed);
                assertTrue(pack.tiers().stream().filter(Rarity::isTop).count() <= 2, "seed " + seed);
            }
        }
    }

    @Test
    @DisplayName("a card's tier is not its species' rarity: a legendary species can be common, and a common species epic or better")
    void tiersAreIndependent() throws IOException {
        List<RentalSetDefinition> pool = pool();
        boolean legendaryCommon = false, commonEpic = false;
        for (long seed = 0; seed < 3000; seed++) {
            for (Pack pack : RentalDraw.draw(pool, seed, true).packs()) {
                for (int i = 0; i < pack.cards().size(); i++) {
                    Rarity species = pack.cards().get(i).rarity(), tier = pack.tiers().get(i);
                    legendaryCommon |= species.isTop() && tier == Rarity.COMMON;
                    commonEpic |= species == Rarity.COMMON && tier.atLeast(Rarity.EPIC);
                }
            }
        }
        assertTrue(legendaryCommon, "a top-class species never came up at common tier");
        assertTrue(commonEpic, "a common-class species never came up at epic tier or better");
    }

    @Test
    @DisplayName("the cap counts the tier on the card: a legendary species at common tier does not count, a common species at mythic does")
    void capCountsTiers() throws IOException {
        List<RentalSetDefinition> pool = pool();
        RentalSetDefinition a = ofClass(pool, Rarity.COMMON, 0), b = ofClass(pool, Rarity.COMMON, 1), c = ofClass(pool, Rarity.COMMON, 2);
        RentalSetDefinition d = ofClass(pool, Rarity.COMMON, 3), e = ofClass(pool, Rarity.COMMON, 4);
        RentalSetDefinition legendary = ofClass(pool, Rarity.LEGENDARY, 0);
        List<RentalSetDefinition> species = List.of(a, b, c, d, e);
        List<Pack> packs = List.of(
                new Pack(species, List.of(Rarity.MYTHIC, Rarity.LEGENDARY, Rarity.COMMON, Rarity.COMMON, Rarity.COMMON), false),
                new Pack(species, List.of(Rarity.LEGENDARY, Rarity.COMMON, Rarity.COMMON, Rarity.COMMON, Rarity.COMMON), false),
                new Pack(List.of(legendary, b, c, d, e), List.of(Rarity.COMMON, Rarity.COMMON, Rarity.COMMON, Rarity.COMMON, Rarity.COMMON), false));
        RentalDraft draft = new RentalDraft(new Offer(packs));
        assertEquals(Result.OK, draft.pick(0, List.of(0, 1)), "two top tiers are allowed");
        assertEquals(Result.TOO_MANY_LEGENDARY, draft.pick(1, List.of(0, 1)), "a third top tier is refused, though the species is common");
        assertEquals(Result.OK, draft.pick(1, List.of(1, 2)));
        assertEquals(Result.OK, draft.pick(2, List.of(0, 1)), "a legendary species at common tier is free to keep");
        assertEquals(List.of(Rarity.MYTHIC, Rarity.LEGENDARY, Rarity.COMMON, Rarity.COMMON, Rarity.COMMON, Rarity.COMMON), draft.teamTiers());
        assertEquals(draft.teamTiers(), draft.finish(UUID::randomUUID).tiers());
    }

    @Test
    @DisplayName("a pack refuses a tier list that does not match its cards")
    void tiersMatchCards() throws IOException {
        List<RentalSetDefinition> pool = pool();
        assertThrows(IllegalArgumentException.class, () -> new Pack(pool.subList(0, 5), List.of(Rarity.COMMON), false));
    }

    @Test
    @DisplayName("restarting forgets the picks and leaves the same offer")
    void restart() throws IOException {
        RentalDraft draft = new RentalDraft(RentalDraw.draw(pool(), 7, false));
        Offer offer = draft.offer();
        draft.pick(0, List.of(0, 1));
        draft.restart();
        assertEquals(0, draft.currentPack());
        assertEquals(offer, draft.offer());
        assertEquals(Result.OK, draft.pick(0, List.of(3, 4)));
    }
}
