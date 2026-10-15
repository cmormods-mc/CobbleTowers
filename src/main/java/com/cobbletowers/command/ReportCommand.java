package com.cobbletowers.command;

import com.cobbletowers.report.ReportService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /tower report <text>} for players and {@code /cobbletowers reportadmin list [count]} for operators (P48). */
public final class ReportCommand {

    private ReportCommand() {}

    public static List<LiteralArgumentBuilder<CommandSourceStack>> playerCommands() {
        return List.of(Commands.literal("report")
                .executes(context -> say(context, "Say what happened: /tower report <what went wrong>."))
                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(ReportCommand::file)));
    }

    public static void registerAdmin(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers").then(Commands.literal("reportadmin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(context -> list(context, 5))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 30))
                                .executes(context -> list(context, IntegerArgumentType.getInteger(context, "count")))))));
    }

    private static int file(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, ReportService.file(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "text")));
    }

    private static int list(CommandContext<CommandSourceStack> context, int count) {
        List<String> lines = ReportService.recent(count);
        if (lines.isEmpty()) return say(context, "No reports yet.");
        for (String line : lines) context.getSource().sendSuccess(() -> Component.literal(line), false);
        return lines.size();
    }

    private static int say(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
