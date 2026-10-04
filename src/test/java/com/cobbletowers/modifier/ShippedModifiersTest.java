package com.cobbletowers.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.ModifierDefinition;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The modifiers this mod ships, read from the real resource files: all parse, all reference real modifiers. */
class ShippedModifiersTest {

    private static Map<ResourceLocation, ModifierDefinition> load() throws IOException {
        Path dir = Paths.get("src/main/resources/data/cobbletowers/cobbletowers/modifiers");
        Map<ResourceLocation, ModifierDefinition> all = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", name);
                try (Reader reader = Files.newBufferedReader(file)) {
                    all.put(id, ModifierDefinition.fromJson(id, JsonParser.parseReader(reader).getAsJsonObject()));
                }
            }
        }
        return all;
    }

    @Test
    @DisplayName("forty-one modifiers ship (25 ordinary, 4 custom, 12 relics), and every one parses and does something its type applies")
    void twentyFiveShip() throws IOException {
        assertEquals(41, load().size());
        assertEquals(12, load().values().stream().filter(ModifierDefinition::relic).count());
    }

    @Test
    @DisplayName("every relic is a single-copy boon with at least one tag")
    void relicsAreSingleCopyAndTagged() throws IOException {
        for (ModifierDefinition modifier : load().values()) {
            if (!modifier.relic()) continue;
            assertEquals(1, modifier.stackLimit(), modifier.id() + " must not stack");
            assertTrue(!modifier.tags().isEmpty(), modifier.id() + " needs a tag");
        }
    }

    @Test
    @DisplayName("every exclusion and requirement names a modifier that exists")
    void referencesResolve() throws IOException {
        Map<ResourceLocation, ModifierDefinition> all = load();
        List<String> dangling = new ArrayList<>();
        for (ModifierDefinition modifier : all.values()) {
            for (ResourceLocation other : modifier.excludes()) {
                if (!all.containsKey(other)) dangling.add(modifier.id() + " excludes " + other);
            }
            for (ResourceLocation other : modifier.requires()) {
                if (!all.containsKey(other)) dangling.add(modifier.id() + " requires " + other);
            }
        }
        assertTrue(dangling.isEmpty(), dangling.toString());
    }

    @Test
    @DisplayName("a requirement can be met: the modifier it requires is not one that excludes it")
    void requirementsAreReachable() throws IOException {
        Map<ResourceLocation, ModifierDefinition> all = load();
        for (ModifierDefinition modifier : all.values()) {
            for (ResourceLocation required : modifier.requires()) {
                assertTrue(!all.get(required).excludes().contains(modifier.id()),
                        modifier.id() + " requires " + required + ", which excludes it");
            }
        }
    }
}
