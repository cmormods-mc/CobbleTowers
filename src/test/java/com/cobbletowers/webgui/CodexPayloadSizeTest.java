package com.cobbletowers.webgui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.menu.ModifierMenuText;
import com.cobbletowers.network.TowerFeatureState;
import com.google.gson.JsonParser;
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

/** The codex menus are the biggest page payloads; WebGUI drops one over 32,000 characters, so they must stay clear of it. */
class CodexPayloadSizeTest {

    @Test
    @DisplayName("the modifier and relic codex payloads stay under WebGUI's emit limit with room to grow")
    void codexFitsTheEmitLimit() throws Exception {
        Path dir = Paths.get("src/main/resources/data/cobbletowers/cobbletowers/modifiers");
        List<TowerFeatureState.Entry> relics = new ArrayList<>(), plain = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", file.getFileName().toString().replace(".json", ""));
                ModifierDefinition m;
                try (Reader reader = Files.newBufferedReader(file)) {
                    m = ModifierDefinition.fromJson(id, JsonParser.parseReader(reader).getAsJsonObject());
                }
                var entry = new TowerFeatureState.Entry(id.toString(), m.displayName(), ModifierMenuText.lines(m), List.of());
                if (m.relic() || m.tagBonus().isPresent()) relics.add(entry); else plain.add(entry);
            }
        }
        int relicSize = size("relics", relics), plainSize = size("modifiers", plain);
        System.out.println("codex payload characters: relics=" + relicSize + " modifiers=" + plainSize);
        assertTrue(relicSize < 24_000, "relic codex is " + relicSize + " characters");
        assertTrue(plainSize < 24_000, "modifier codex is " + plainSize + " characters");
    }

    private static int size(String section, List<TowerFeatureState.Entry> entries) {
        return PayloadJson.envelope(new TowerFeatureState(1, section, "Codex", entries, List.of(), "")).toString().length();
    }
}
