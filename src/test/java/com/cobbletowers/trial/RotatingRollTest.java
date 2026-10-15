package com.cobbletowers.trial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RotatingRecipeDefinition;
import com.cobbletowers.definition.TrialPoolDefinition.Kind;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The pure roll of a rotating trial (P46a) and the shipped recipes it draws from. */
class RotatingRollTest {

    private static final Path DATA = Paths.get("src/main/resources/data/cobbletowers/cobbletowers");

    private static JsonObject json(String relative) throws Exception {
        try (Reader reader = Files.newBufferedReader(DATA.resolve(relative))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static RotatingRecipeDefinition recipe(String name) throws Exception {
        return RotatingRecipeDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("cobbletowers", name), json("trial_recipes/" + name + ".json"));
    }

    private static boolean exists(String folder, ResourceLocation id) {
        return Files.exists(DATA.resolve(folder + "/" + id.getPath() + ".json"));
    }

    @Test
    @DisplayName("the same recipe and period always roll the same trial")
    void deterministic() throws Exception {
        RotatingRecipeDefinition daily = recipe("daily");
        for (long day = 20000; day < 20100; day++) assertEquals(RotatingRoll.of(daily, day), RotatingRoll.of(daily, day));
        assertNotEquals(RotatingRoll.of(daily, 20000), RotatingRoll.of(daily, 20001));
    }

    @Test
    @DisplayName("no two consecutive periods share a region path or a house rule over 400 periods")
    void noRepeats() throws Exception {
        for (String name : List.of("daily", "weekly")) {
            RotatingRecipeDefinition recipe = recipe(name);
            RotatingRoll previous = RotatingRoll.of(recipe, 20000);
            for (long n = 20001; n < 20400; n++) {
                RotatingRoll roll = RotatingRoll.of(recipe, n);
                assertNotEquals(previous.segmentRegions(), roll.segmentRegions(), name + " path repeated at " + n);
                if (previous.playlist().isPresent()) assertNotEquals(previous.playlist(), roll.playlist(), name + " playlist repeated at " + n);
                previous = roll;
            }
        }
    }

    @Test
    @DisplayName("a roll is well formed: segments match the floors, neighbours differ unless the path is one region, mutators are distinct groups")
    void wellFormed() throws Exception {
        for (String name : List.of("daily", "weekly")) {
            RotatingRecipeDefinition recipe = recipe(name);
            Set<ResourceLocation> regionsSeen = new HashSet<>();
            int mixed = 0;
            int withMutators = 0;
            int featured = 0;
            for (long n = 0; n < 400; n++) {
                RotatingRoll roll = RotatingRoll.of(recipe, n);
                assertEquals(recipe.segments(), roll.segmentRegions().size());
                Set<ResourceLocation> distinct = new HashSet<>(roll.segmentRegions());
                regionsSeen.addAll(distinct);
                if (distinct.size() > 1) {
                    mixed++;
                    for (int i = 1; i < roll.segmentRegions().size(); i++) {
                        assertNotEquals(roll.segmentRegions().get(i - 1), roll.segmentRegions().get(i));
                    }
                }
                assertTrue(recipe.levels().contains(roll.level()));
                assertTrue(roll.mutators().size() <= 2 && new HashSet<>(roll.mutators()).size() == roll.mutators().size());
                if (!roll.mutators().isEmpty()) withMutators++;
                if (roll.featured()) {
                    featured++;
                    assertTrue(roll.namedIndex() < recipe.named().size());
                }
                assertEquals(recipe.weeklyRules().isEmpty(), roll.weeklyRule().isEmpty());
                assertEquals(roll.segmentRegions().get(0), roll.regionAt(recipe, 1));
                assertEquals(roll.segmentRegions().get(roll.segmentRegions().size() - 1), roll.regionAt(recipe, recipe.floors()));
            }
            assertEquals(recipe.regions().size(), regionsSeen.size(), name + " never used some region");
            assertTrue(mixed > 250 && mixed < 400, name + " mixed " + mixed);
            assertTrue(withMutators > 200, name + " mutators " + withMutators);
            assertTrue(featured >= 400 / recipe.featuredEvery() && featured <= 400 / recipe.featuredEvery() + 1, name + " featured days " + featured);
        }
    }

    @Test
    @DisplayName("every id a shipped recipe names exists: regions, playlists, mutators (not relics) and starting relics (relics)")
    void shippedRecipesResolve() throws Exception {
        for (String name : List.of("daily", "weekly")) {
            RotatingRecipeDefinition recipe = recipe(name);
            for (ResourceLocation region : recipe.regions()) assertTrue(exists("towers", region), "region " + region);
            for (ResourceLocation playlist : recipe.playlists()) assertTrue(exists("playlists", playlist), "playlist " + playlist);
            for (var mutator : recipe.mutators()) {
                assertTrue(exists("modifiers", mutator.id()), "mutator " + mutator.id());
                assertFalse(json("modifiers/" + mutator.id().getPath() + ".json").has("relic") && json("modifiers/" + mutator.id().getPath() + ".json").get("relic").getAsBoolean(),
                        mutator.id() + " is a relic");
            }
            for (ResourceLocation relic : recipe.relics()) {
                assertTrue(exists("modifiers", relic), "relic " + relic);
                assertTrue(json("modifiers/" + relic.getPath() + ".json").get("relic").getAsBoolean(), relic + " is not a relic");
            }
            for (var entry : recipe.named()) {
                assertTrue(exists("towers", entry.tower()), "named tower " + entry.tower());
                entry.playlist().ifPresent(p -> assertTrue(exists("playlists", p)));
                for (ResourceLocation m : entry.modifiers()) assertTrue(exists("modifiers", m), "named modifier " + m);
            }
            assertEquals(name.equals("daily") ? Kind.DAILY : Kind.WEEKLY, recipe.kind());
        }
    }

    @Test
    @DisplayName("a recipe with nothing to draw from, or a bad number, is rejected at load")
    void invalidRecipes() throws Exception {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", "x");
        JsonObject noRegions = json("trial_recipes/daily.json");
        noRegions.add("regions", new com.google.gson.JsonArray());
        assertThrows(IllegalArgumentException.class, () -> RotatingRecipeDefinition.fromJson(id, noRegions));
        JsonObject noLevels = json("trial_recipes/daily.json");
        noLevels.add("levels", new com.google.gson.JsonArray());
        assertThrows(IllegalArgumentException.class, () -> RotatingRecipeDefinition.fromJson(id, noLevels));
        JsonObject tooMany = json("trial_recipes/daily.json");
        tooMany.addProperty("segment_size", 9);
        assertThrows(IllegalArgumentException.class, () -> RotatingRecipeDefinition.fromJson(id, tooMany));
        JsonObject dailyRule = json("trial_recipes/daily.json");
        var rules = new com.google.gson.JsonArray();
        rules.add("no_vendor");
        dailyRule.add("weekly_rules", rules);
        assertThrows(IllegalArgumentException.class, () -> RotatingRecipeDefinition.fromJson(id, dailyRule));
        JsonObject emptyNamed = json("trial_recipes/daily.json");
        emptyNamed.add("named", new com.google.gson.JsonArray());
        assertThrows(IllegalArgumentException.class, () -> RotatingRecipeDefinition.fromJson(id, emptyNamed));
    }
}
