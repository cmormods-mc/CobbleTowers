package com.cobbletowers.dev;

import com.cobbletowers.intermission.IntermissionService;
import com.cobbletowers.runtime.TowerRuns;
import com.cobbletowers.webgui.PayloadJson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cobbletowers web dump intermission <player>}: writes the JSON the player's intermission page would receive right now to
 * {@code payload-dumps/} in the server folder, so the real page can be drawn from a real run's data (screenshots). Dev build only.
 */
public final class PayloadDumpCommand {

    private PayloadDumpCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers")
                .then(Commands.literal("web")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("dump")
                                .then(Commands.literal("intermission")
                                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                            var run = TowerRuns.forPlayer(player.getUUID());
                                            if (run.isEmpty()) {
                                                source.sendFailure(Component.literal(player.getGameProfile().getName() + " is not in a run."));
                                                return 0;
                                            }
                                            var payload = IntermissionService.payloadOf(source.getServer(), run.get(), player, "", true);
                                            Path file = Path.of("payload-dumps", "intermission-" + player.getGameProfile().getName() + "-f" + run.get().floorIndex() + ".json");
                                            try {
                                                Files.createDirectories(file.getParent());
                                                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(PayloadJson.envelope(payload)), StandardCharsets.UTF_8);
                                            } catch (IOException ex) {
                                                source.sendFailure(Component.literal("Could not write " + file + ": " + ex.getMessage()));
                                                return 0;
                                            }
                                            source.sendSuccess(() -> Component.literal("Wrote " + file), false);
                                            return 1;
                                        }))))));
    }
}
