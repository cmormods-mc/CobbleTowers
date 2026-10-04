package com.cobbletowers.rental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.definition.RentalSetDefinition.Rarity;
import com.cobbletowers.rental.RentalCards.Spec;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The card every rental set makes (P33b): pure, stable, and in CobblemonCards' own vocabulary. */
class RentalCardsTest {

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

    private static RentalSetDefinition set(String species) throws IOException {
        return pool().stream().filter(s -> s.species().equals(species)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("every shipped set names its types, and they are real ones")
    void everySetHasTypes() throws IOException {
        for (RentalSetDefinition set : pool()) {
            assertTrue(!set.types().isEmpty() && set.types().size() <= 2, set.species());
            assertTrue(RentalSetDefinition.TYPE_NAMES.contains(set.primaryType()), set.species());
        }
        assertEquals("dragon", set("garchomp").primaryType());
        assertEquals(List.of("dragon", "ground"), set("garchomp").types());
    }

    @Test
    @DisplayName("a card is the set's species, rarity and type, in the mod's field names")
    void cardOfASet() throws IOException {
        Spec card = RentalCards.of(set("garchomp"), false, Rarity.EPIC);
        assertEquals("garchomp", card.pokemonId());
        assertEquals("epic", card.rarity());
        assertEquals("dragon_spawn", card.stat());
        assertFalse(card.shiny());
        assertEquals(0, card.grade());
        assertEquals("galactic_supernova", card.background().orElse("none"));
        assertEquals("holo_sparkle", card.effect().orElseThrow());

        CompoundTag component = card.toComponent();
        assertEquals("garchomp", component.getString("pokemon_id"));
        assertEquals(0, component.getByte("is_shiny"));
        assertEquals("epic", component.getString("rarity"));
        assertEquals("dragon_spawn", component.getString("stat"));
        assertEquals(0.10f, component.getFloat("stat_value"), 1e-6f);
        assertEquals(0, component.getInt("grade"));
        assertTrue(component.contains("background") && component.contains("effect"));
    }

    @Test
    @DisplayName("the item tag has the id, a count of one and the card data under the mod's component id")
    void itemTag() throws IOException {
        CompoundTag tag = RentalCards.of(set("lucario"), false, Rarity.EPIC).toItemTag();
        assertEquals("cobblemon-cards:card", tag.getString("id"));
        assertEquals(1, tag.getInt("count"));
        CompoundTag components = tag.getCompound("components");
        assertTrue(components.contains("cobblemon-cards:card_data"));
        assertEquals("lucario", components.getCompound("cobblemon-cards:card_data").getString("pokemon_id"));
    }

    @Test
    @DisplayName("the cap holds: a legendary set makes an epic card when the cap is epic, and its own rarity when the cap allows it")
    void cap() throws IOException {
        assertEquals("epic", RentalCards.of(set("mewtwo"), false, Rarity.EPIC).rarity());
        assertEquals("legendary", RentalCards.of(set("mewtwo"), false, Rarity.LEGENDARY).rarity());
        assertEquals("common", RentalCards.of(set("machamp"), false, Rarity.EPIC).rarity(), "a cap never raises a card");
        assertEquals("rare", RentalCards.of(set("garchomp"), false, Rarity.RARE).rarity());
    }

    @Test
    @DisplayName("a card from a God Pack is shiny and a little stronger, as the mod's own God Pack cards are")
    void godPack() throws IOException {
        Spec plain = RentalCards.of(set("kingambit"), false, Rarity.EPIC);
        Spec shiny = RentalCards.of(set("kingambit"), true, Rarity.EPIC);
        assertTrue(shiny.shiny() && !plain.shiny());
        assertEquals(plain.statValue() + 0.03f, shiny.statValue(), 1e-6f);
        assertEquals(plain.rarity(), shiny.rarity());
    }

    @Test
    @DisplayName("looks grow with rarity: a common card is plain, an uncommon one has a background, rare and up shimmer")
    void looksGrowWithRarity() throws IOException {
        Spec common = RentalCards.of(set("machamp"), false, Rarity.EPIC);
        assertTrue(common.background().isEmpty() && common.effect().isEmpty());
        Spec uncommon = RentalCards.of(set("snorlax"), false, Rarity.EPIC);
        assertTrue(uncommon.background().isPresent() && uncommon.effect().isEmpty());
        Spec rare = RentalCards.of(set("tyranitar"), false, Rarity.EPIC);
        assertTrue(rare.background().isPresent() && rare.effect().isPresent());
        // a shiny common card still gets a background: the mod gives every shiny card one
        assertTrue(RentalCards.of(set("machamp"), true, Rarity.EPIC).background().isPresent());
    }

    @Test
    @DisplayName("stat values rise with rarity and stay inside the mod's own ranges")
    void statValues() throws IOException {
        float[] low = {0.005f, 0.015f, 0.04f, 0.08f, 0.12f, 0.20f};
        float[] high = {0.010f, 0.030f, 0.07f, 0.12f, 0.18f, 0.25f};
        float previous = 0f;
        for (Rarity rarity : Rarity.values()) {
            // one set of each rarity, capped at mythic so the rarity is the set's own
            RentalSetDefinition any = pool().stream().filter(s -> s.rarity() == rarity).findFirst().orElse(null);
            if (any == null) continue;   // there is no mythic set today
            float value = RentalCards.of(any, false, Rarity.MYTHIC).statValue();
            assertTrue(value >= low[rarity.ordinal()] && value <= high[rarity.ordinal()], rarity + " " + value);
            assertTrue(value > previous, rarity + " must be above the one before");
            previous = value;
        }
    }

    @Test
    @DisplayName("every set makes a card whose names are in the mod's vocabulary, and the same card every time")
    void everySetIsStableAndValid() throws IOException {
        List<String> stats = new ArrayList<>();
        for (String type : RentalSetDefinition.TYPE_NAMES) stats.add(type + "_spawn");
        for (RentalSetDefinition set : pool()) {
            Spec a = RentalCards.of(set, false, Rarity.EPIC);
            Spec b = RentalCards.of(set, false, Rarity.EPIC);
            assertEquals(a, b, set.species());
            assertTrue(stats.contains(a.stat()), set.species() + " " + a.stat());
            assertTrue(List.of("common", "uncommon", "rare", "epic").contains(a.rarity()), set.species());
        }
    }

    @Test
    @DisplayName("the label a player reads names the Pokemon, the rarity and a God Pack's shine")
    void label() throws IOException {
        RentalSetDefinition garchomp = set("garchomp");
        assertEquals("Garchomp card (epic)", RentalCards.label(garchomp, RentalCards.of(garchomp, false, Rarity.EPIC)));
        assertEquals("Garchomp card (shiny epic)", RentalCards.label(garchomp, RentalCards.of(garchomp, true, Rarity.EPIC)));
    }
}
