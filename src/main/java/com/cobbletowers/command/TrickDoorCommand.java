package com.cobbletowers.command;

import com.cobbletowers.halloween.door.TrickDoors;
import com.cobbletowers.instance.TowerDimension;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Operator tool for the Trick or Treat doors (P57): {@code /cobbletowers trickdoor list|open|close}. For placing and testing, not a player command. */
public final class TrickDoorCommand {

    private TrickDoorCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers").then(Commands.literal("trickdoor")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(context -> {
                    var lines = TrickDoors.describe();
                    context.getSource().sendSuccess(() -> Component.literal(lines.isEmpty() ? "No trick doors." : String.join("\n", lines)), false);
                    return lines.size();
                }))
                .then(Commands.literal("open").executes(context -> set(context.getSource(), true)))
                .then(Commands.literal("close").executes(context -> set(context.getSource(), false)))));
    }

    private static int set(CommandSourceStack source, boolean open) {
        ServerLevel level = TowerDimension.level(source.getServer());
        if (level == null) {
            source.sendFailure(Component.literal("The tower dimension is not loaded."));
            return 0;
        }
        int changed = TrickDoors.setAll(level, open);
        source.sendSuccess(() -> Component.literal(changed + " trick door(s) " + (open ? "opened" : "shut") + "."), false);
        return changed;
    }
}
