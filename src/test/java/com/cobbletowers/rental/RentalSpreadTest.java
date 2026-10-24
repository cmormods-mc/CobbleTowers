package com.cobbletowers.rental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.rental.RentalDraw.Pack;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The IVs and EVs a rental card rolls. */
class RentalSpreadTest {

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

    @Test
    @DisplayName("every rolled spread has exactly two stats at 252 EVs, the set's main stat among them, and IVs of 15 to 31")
    void rules() throws IOException {
        List<RentalSetDefinition> pool = pool();
        for (long seed = 0; seed < 60; seed++) {
            for (Pack pack : RentalDraw.draw(pool, seed, true).packs()) {
                for (int i = 0; i < pack.cards().size(); i++) {
                    RentalSetDefinition set = pack.cards().get(i);
                    RentalSpread spread = pack.spreads().get(i);
                    assertEquals(2, spread.evs().stream().filter(ev -> ev == 252).count(), set.species() + " seed " + seed);
                    assertEquals(504, spread.evs().stream().mapToInt(Integer::intValue).sum());
                    int main = 0;
                    for (int s = 1; s < 6; s++) if (set.evs().get(s) > set.evs().get(main)) main = s;
                    if (set.evs().get(main) > 0) assertEquals(252, spread.evs().get(main), set.species() + " keeps its main stat");
                    for (int iv : spread.ivs()) assertTrue(iv >= 15 && iv <= 31, "iv " + iv);
                    if (set.evs().get(5) > 0) assertEquals(31, spread.ivs().get(5), set.species() + " keeps a perfect Speed IV");
                }
            }
        }
    }

    @Test
    @DisplayName("the same seed rolls the same spreads, and the spreads vary between cards")
    void seeded() throws IOException {
        List<RentalSetDefinition> pool = pool();
        assertEquals(RentalDraw.draw(pool, 7, false), RentalDraw.draw(pool, 7, false));
        long distinct = RentalDraw.draw(pool, 7, false).packs().stream().flatMap(p -> p.spreads().stream()).distinct().count();
        assertTrue(distinct > 10, "fifteen cards, mostly different spreads: " + distinct);
    }
}
