package com.cobbletowers.command;

import com.cobbletowers.reward.RewardDelivery;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /tower claim}: takes the rewards that did not fit in a full inventory. */
public final class ClaimCommand {

    private ClaimCommand() {}

    public static List<LiteralArgumentBuilder<CommandSourceStack>> playerCommands() {
        return List.of(Commands.literal("claim").executes(ClaimCommand::claim));
    }

    private static int claim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        int placed = RewardDelivery.claim(context.getSource().getServer(), player);
        if (placed == 0) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Nothing to claim, or your inventory is still full."), false);
        }
        return placed;
    }
}
