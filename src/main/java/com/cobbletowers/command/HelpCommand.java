package com.cobbletowers.command;

import com.cobbletowers.TowerLog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /tower help} (P48): the same guide the web Hall shows, from {@code data/cobbletowers/guide.json}, in chat for
 * players who have no page.
 */
public final class HelpCommand {

    private HelpCommand() {}

    /** One guide section. */
    public record Section(String heading, List<String> lines) {}

    public static List<LiteralArgumentBuilder<CommandSourceStack>> playerCommands() {
        return List.of(Commands.literal("help").executes(context -> {
            var source = context.getSource();
            source.sendSuccess(() -> Component.literal(title()).withStyle(ChatFormatting.GOLD), false);
            for (Section section : sections()) {
                source.sendSuccess(() -> Component.literal(section.heading()).withStyle(ChatFormatting.YELLOW), false);
                for (String line : section.lines()) source.sendSuccess(() -> Component.literal("  " + line), false);
            }
            return 1;
        }));
    }

    private static JsonObject read() {
        try (InputStream in = HelpCommand.class.getResourceAsStream("/data/cobbletowers/guide.json")) {
            if (in == null) return new JsonObject();
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException | RuntimeException ex) {
            TowerLog.error("Could not read the player guide", ex);
            return new JsonObject();
        }
    }

    public static String title() {
        JsonObject root = read();
        return root.has("title") ? root.get("title").getAsString() : "CobbleTowers";
    }

    public static List<Section> sections() {
        List<Section> out = new ArrayList<>();
        JsonObject root = read();
        if (!root.has("sections")) return out;
        for (JsonElement element : root.getAsJsonArray("sections")) {
            JsonObject section = element.getAsJsonObject();
            List<String> lines = new ArrayList<>();
            for (JsonElement line : section.getAsJsonArray("lines")) lines.add(line.getAsString());
            out.add(new Section(section.get("heading").getAsString(), lines));
        }
        return out;
    }
}
